package io.vidocq.tools.dashboard.store;

import io.vidocq.tools.dashboard.domain.CommitKind;
import io.vidocq.tools.dashboard.domain.Interval;
import io.vidocq.tools.dashboard.domain.StatusSpan;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The schema, the repositories and the aggregate queries, against the PostgreSQL of the dev services. */
class StoreIntegrationTest {

    private static TrackedOrgRepository orgs;
    private static JobExecutionRepository jobs;
    private static CommitStatRepository commits;
    private static QueueSampleRepository queue;
    private static PrStatRepository pulls;
    private static RunnerStatusChangeRepository changes;
    private static StatsQueries stats;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @BeforeAll
    static void startApplication() {
        orgs = InProcessApp.bean(TrackedOrgRepository.class);
        jobs = InProcessApp.bean(JobExecutionRepository.class);
        commits = InProcessApp.bean(CommitStatRepository.class);
        queue = InProcessApp.bean(QueueSampleRepository.class);
        pulls = InProcessApp.bean(PrStatRepository.class);
        changes = InProcessApp.bean(RunnerStatusChangeRepository.class);
        stats = InProcessApp.bean(StatsQueries.class);
    }

    @BeforeEach
    void emptyTables() {
        InProcessApp.truncate("tracked_org", "job_execution", "commit_stat", "queue_sample", "pr_stat",
                "runner_status_change");
    }

    @Test
    void orgsAreSavedAndListedInOrder() {
        orgs.save(new TrackedOrg("VidocqTools", 1, true));
        orgs.save(new TrackedOrg("Archived", 2, false));
        orgs.save(new TrackedOrg("Vidocq", 0, true));

        assertEquals(List.of("Vidocq", "VidocqTools"),
                orgs.findByActiveTrueOrderByPositionAsc().stream().map(TrackedOrg::getName).toList());
        assertTrue(orgs.findByName("Vidocq").orElseThrow().getId() > 0);
    }

    @Test
    void jobExecutionUpsertByTaskId() {
        jobs.save(job(175_790, "Vidocq", now.minus(Duration.ofMinutes(5)), null));
        jobs.save(job(175_790, "Vidocq", now.minus(Duration.ofMinutes(5)), now));

        assertEquals(1, jobs.findAll().count());
        assertEquals(now, jobs.findById(175_790L).orElseThrow().getStoppedAt());
    }

    @Test
    void openExecutionsAreListed() {
        jobs.save(job(1, "Vidocq", now.minus(Duration.ofMinutes(9)), null));
        jobs.save(job(2, "Vidocq", now.minus(Duration.ofMinutes(8)), null));
        jobs.save(job(3, "Vidocq", now.minus(Duration.ofMinutes(7)), now));
        jobs.save(job(4, "VidocqTools", now.minus(Duration.ofMinutes(6)), null));

        assertEquals(2, jobs.findByOrgAndStoppedAtNull("Vidocq").size());
    }

    @Test
    void committerAggregatesGroupByHumanAndAgents() {
        Instant twoDaysAgo = now.minus(Duration.ofDays(2));
        commits.save(commit("a1", twoDaysAgo, CommitKind.HUMAN, "yblazart", "Claude Opus", 10, 2));
        commits.save(commit("a2", twoDaysAgo, CommitKind.HUMAN, "yblazart", "Claude Opus", 5, 1));
        commits.save(commit("a3", twoDaysAgo, CommitKind.HUMAN, "yblazart", null, 1, 1));
        commits.save(commit("a4", twoDaysAgo, CommitKind.BOT, null, null, 100, 0));
        commits.save(commit("old", now.minus(Duration.ofDays(60)), CommitKind.HUMAN, "yblazart", null, 1, 1));

        var rows = stats.committerAggregates(Optional.empty(), now.minus(Duration.ofDays(30)));

        assertEquals(3, rows.size());
        var opus = rows.stream()
                .filter(r -> "yblazart".equals(r.humanKey()) && "Claude Opus".equals(r.agentFamilies()))
                .findFirst().orElseThrow();
        assertEquals(2, opus.commits());
        assertEquals(15, opus.additions());
        assertEquals(3, opus.deletions());
        assertEquals(CommitKind.HUMAN, opus.kind());
        assertEquals(1, rows.stream().filter(r -> r.kind() == CommitKind.BOT).count());
        assertEquals(0, stats.committerAggregates(Optional.of("VidocqTools"), now.minus(Duration.ofDays(30))).size());
    }

    @Test
    void jobIntervalsAreClippedToWindowByCaller() {
        Instant from = now.minus(Duration.ofHours(6));
        jobs.save(job(10, "Vidocq", from.minus(Duration.ofHours(1)), from.plus(Duration.ofMinutes(10))));
        jobs.save(job(11, "Vidocq", from.minus(Duration.ofHours(3)), from.minus(Duration.ofHours(2))));
        jobs.save(job(12, "Vidocq", from.plus(Duration.ofHours(1)), null));

        List<Interval> intervals = stats.jobIntervals("Vidocq", from, now);

        assertEquals(List.of(new Interval(7, from.minus(Duration.ofHours(1)), from.plus(Duration.ofMinutes(10))),
                new Interval(7, from.plus(Duration.ofHours(1)), null)), intervals);
    }

    @Test
    void statusChangesIncludeTheLastOneBeforeTheWindow() {
        Instant from = now.minus(Duration.ofHours(6));
        changes.save(new RunnerStatusChange(7, "Vidocq", "offline", from.minus(Duration.ofHours(3))));
        changes.save(new RunnerStatusChange(7, "Vidocq", "idle", from.minus(Duration.ofHours(2))));
        changes.save(new RunnerStatusChange(7, "Vidocq", "active", from.plus(Duration.ofHours(1))));
        changes.save(new RunnerStatusChange(8, "Vidocq", "idle", from.plus(Duration.ofHours(2))));
        changes.save(new RunnerStatusChange(9, "VidocqTools", "idle", from.plus(Duration.ofHours(2))));

        assertEquals(List.of(new StatusSpan(7, "idle", from.minus(Duration.ofHours(2))),
                        new StatusSpan(7, "active", from.plus(Duration.ofHours(1))),
                        new StatusSpan(8, "idle", from.plus(Duration.ofHours(2)))),
                stats.statusChanges("Vidocq", from, now));
    }

    @Test
    void prCountsByLogin() {
        pulls.save(pull("Vidocq/vauban#1", "yblazart", now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(1))));
        pulls.save(pull("Vidocq/vauban#2", "yblazart", now.minus(Duration.ofDays(2)), null));
        pulls.save(pull("Vidocq/vauban#3", "other", now.minus(Duration.ofDays(90)), now.minus(Duration.ofDays(2))));

        var counts = stats.prCounts(Optional.empty(), now.minus(Duration.ofDays(30)));

        assertEquals(List.of(new PrCounts("other", 0, 1), new PrCounts("yblazart", 2, 1)), counts);
    }

    @Test
    void purgeRemovesOldRows() {
        jobs.save(job(20, "Vidocq", now.minus(Duration.ofDays(201)), now.minus(Duration.ofDays(200))));
        jobs.save(job(21, "Vidocq", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1))));
        queue.save(new QueueSample("Vidocq", now.minus(Duration.ofDays(40)), 1, 2));
        queue.save(new QueueSample("Vidocq", now.minus(Duration.ofDays(1)), 1, 2));

        assertEquals(2, stats.purge(now.minus(Duration.ofDays(180)), now.minus(Duration.ofDays(30))));
        assertEquals(1, jobs.findAll().count());
        assertEquals(1, queue.findAll().count());
    }

    @Test
    void nullableColumnsRoundTrip() {
        jobs.save(job(30, "Vidocq", now, null));
        var read = jobs.findById(30L).orElseThrow();
        assertNull(read.getStoppedAt());
        assertNull(read.getPrNumber());
        assertNotNull(read.getStartedAt());
    }

    private static JobExecution job(long taskId, String org, Instant startedAt, Instant stoppedAt) {
        var j = new JobExecution();
        j.setTaskId(taskId);
        j.setJobId(taskId + 1000);
        j.setRunId(86_097L);
        j.setOrg(org);
        j.setRepo("vauban");
        j.setRunnerId(7L);
        j.setJobName("build");
        j.setStatus(stoppedAt == null ? "running" : "success");
        j.setStartedAt(startedAt);
        j.setStoppedAt(stoppedAt);
        return j;
    }

    private static CommitStat commit(String sha, Instant at, CommitKind kind, String humanKey, String agents,
                                     int additions, int deletions) {
        var c = new CommitStat();
        c.setId("Vidocq/vauban@" + sha);
        c.setSha(sha);
        c.setOrg("Vidocq");
        c.setRepo("vauban");
        c.setAuthoredAt(at);
        c.setAuthorName(humanKey == null ? "Vidocq Bot" : humanKey);
        c.setKind(kind);
        c.setHumanKey(humanKey);
        c.setAgentFamilies(agents);
        c.setAdditions(additions);
        c.setDeletions(deletions);
        return c;
    }

    private static PrStat pull(String id, String login, Instant createdAt, Instant mergedAt) {
        var p = new PrStat();
        p.setId(id);
        p.setOrg("Vidocq");
        p.setRepo("vauban");
        p.setNumber(Integer.parseInt(id.substring(id.indexOf('#') + 1)));
        p.setAuthorLogin(login);
        p.setCreatedAt(createdAt);
        p.setMergedAt(mergedAt);
        return p;
    }
}
