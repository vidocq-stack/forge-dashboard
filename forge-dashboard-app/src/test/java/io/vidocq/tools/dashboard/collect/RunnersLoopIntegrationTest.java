package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.FakeForgeApi;
import io.vidocq.tools.dashboard.forge.ForgeJob;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.RunnerView;
import io.vidocq.tools.dashboard.store.JobExecution;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.QueueSampleRepository;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.store.RunnerStatusChangeRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.testing.TestClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static io.vidocq.tools.dashboard.collect.ForgeFixtures.finished;
import static io.vidocq.tools.dashboard.collect.ForgeFixtures.job;
import static io.vidocq.tools.dashboard.collect.ForgeFixtures.logHead;
import static io.vidocq.tools.dashboard.collect.ForgeFixtures.run;
import static io.vidocq.tools.dashboard.collect.ForgeFixtures.runner;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The runners loop against a fake CodeFloe and the PostgreSQL of the dev services. */
class RunnersLoopIntegrationTest {

    private static final String ORG = "Vidocq";
    private static final String U = "f924a289-a095-41cb-aebb-326899bb485b";
    private static final Instant T0 = Instant.parse("2026-11-14T12:00:00Z");
    private static final Instant HEAD_AT = Instant.parse("2026-11-14T11:59:50.506107Z");

    private static JobExecutionRepository jobs;
    private static RunnerRowRepository runners;
    private static RunnerStatusChangeRepository changes;
    private static QueueSampleRepository queue;

    private FakeForgeApi forge;
    private LiveState live;
    private TestClock clock;

    @BeforeAll
    static void startApplication() {
        jobs = InProcessApp.bean(JobExecutionRepository.class);
        runners = InProcessApp.bean(RunnerRowRepository.class);
        changes = InProcessApp.bean(RunnerStatusChangeRepository.class);
        queue = InProcessApp.bean(QueueSampleRepository.class);
    }

    @BeforeEach
    void reset() {
        InProcessApp.truncate("runner", "runner_status_change", "job_execution", "queue_sample");
        forge = new FakeForgeApi();
        live = new LiveState(new ChangeBus());
        clock = new TestClock(T0);
        forge.runners.put(ORG, List.of(runner(1219, U, "active")));
        forge.runs.put(86_414L, run("pull_request", "#298"));
    }

    private RunnersLoop loop() {
        return new RunnersLoop(forge, jobs, runners, changes, queue, live, clock);
    }

    private void running(ForgeJob... active) {
        forge.activeJobs.put(ORG, List.of(active));
    }

    @Test
    void newRunningJobIsRecordedWithItsRunner() {
        ForgeJob j = job(175_790, "running");
        running(j);
        forge.logHeads.put(j.id(), logHead(HEAD_AT, U, 175_790));

        loop().tick(ORG);

        JobExecution e = jobs.findById(175_790L).orElseThrow();
        assertEquals(1219L, e.getRunnerId());
        assertEquals(HEAD_AT, e.getStartedAt());
        assertEquals(298, e.getPrNumber());
        assertEquals("vidocq", e.getRepo());
        assertNull(e.getStoppedAt());
    }

    @Test
    void missingLogHeadIsRetriedNextTick() {
        ForgeJob j = job(175_790, "running");
        running(j);
        RunnersLoop loop = loop();

        loop.tick(ORG);
        assertNull(jobs.findById(175_790L).orElseThrow().getRunnerId());
        assertEquals(T0, jobs.findById(175_790L).orElseThrow().getStartedAt());

        forge.logHeads.put(j.id(), logHead(HEAD_AT, U, 175_790));
        clock.advance(Duration.ofSeconds(15));
        loop.tick(ORG);

        assertEquals(1219L, jobs.findById(175_790L).orElseThrow().getRunnerId());
        assertEquals(HEAD_AT, jobs.findById(175_790L).orElseThrow().getStartedAt());
    }

    @Test
    void runIsFetchedOncePerRun() {
        running(job(1, 86_414, 175_790, "running", "vidocq"), job(2, 86_414, 175_791, "running", "vidocq"));
        RunnersLoop loop = loop();
        loop.tick(ORG);
        loop.tick(ORG);
        assertEquals(1, forge.calls.get("getRun"));
    }

    @Test
    void openExecutionsAreReconciledAfterRestart() {
        ForgeJob j = job(175_790, "running");
        running(j);
        forge.logHeads.put(j.id(), logHead(HEAD_AT, U, 175_790));
        loop().tick(ORG);

        Instant stopped = T0.plusSeconds(40);
        forge.jobs.put(j.id(), finished(j, HEAD_AT, stopped, "success"));
        running();
        clock.advance(Duration.ofMinutes(1));
        loop().tick(ORG);   // a new instance: nothing in memory

        JobExecution e = jobs.findById(175_790L).orElseThrow();
        assertEquals(stopped, e.getStoppedAt());
        assertEquals("success", e.getStatus());
        assertEquals(0, jobs.findByOrgAndStoppedAtNull(ORG).size());
    }

    @Test
    void vanishedJobWithoutDetailIsClosedNow() {
        ForgeJob j = job(175_790, "running");
        running(j);
        loop().tick(ORG);
        running();
        clock.advance(Duration.ofSeconds(15));
        loop().tick(ORG);   // getJob: not found

        assertEquals(T0.plusSeconds(15), jobs.findById(175_790L).orElseThrow().getStoppedAt());
    }

    @Test
    void runnerStatusChangeIsRecordedOnce() {
        forge.runners.put(ORG, List.of(runner(1219, U, "idle")));
        RunnersLoop loop = loop();
        loop.tick(ORG);
        long first = changes.findAll().count();

        forge.runners.put(ORG, List.of(runner(1219, U, "active")));
        loop.tick(ORG);
        assertEquals(first + 1, changes.findAll().count());

        loop.tick(ORG);
        assertEquals(first + 1, changes.findAll().count());
        assertEquals("active", runners.findById(1219L).orElseThrow().getLastStatus());
    }

    @Test
    void liveSnapshotGroupsRunningJobsPerRunner() {
        ForgeJob a = job(1, 86_414, 175_790, "running", "vidocq");
        ForgeJob b = job(2, 86_414, 175_791, "running", "vauban");
        ForgeJob w = job(3, 86_414, 0, "waiting", "vauban");
        running(a, b, w);
        forge.logHeads.put(a.id(), logHead(HEAD_AT, U, 175_790));
        forge.logHeads.put(b.id(), logHead(HEAD_AT, U, 175_791));

        loop().tick(ORG);

        RunnerView view = live.snapshot(ORG).runners().getFirst();
        assertEquals(2, view.running().size());
        assertEquals("runner-1219", view.running().getFirst().runnerName());
        assertEquals(2, view.capacity());   // observed: both at once
        assertEquals(1, live.snapshot(ORG).waiting().size());
        assertEquals(T0, live.snapshot(ORG).updatedAt());
    }

    @Test
    void queueIsSampledOncePerMinuteWithItsMaximum() {
        running(job(3, 86_414, 0, "waiting", "vauban"));
        RunnersLoop loop = loop();
        loop.tick(ORG);
        running(job(3, 86_414, 0, "waiting", "vauban"), job(4, 86_414, 0, "waiting", "vauban"));
        clock.advance(Duration.ofSeconds(15));
        loop.tick(ORG);
        running();
        clock.advance(Duration.ofSeconds(15));
        loop.tick(ORG);

        var samples = queue.findAll().toList();
        assertEquals(1, samples.size());
        assertEquals(2, samples.getFirst().getWaiting());
        assertEquals(T0, samples.getFirst().getMinute());
    }
}
