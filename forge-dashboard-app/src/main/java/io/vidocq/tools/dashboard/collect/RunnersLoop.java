package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.domain.Interval;
import io.vidocq.tools.dashboard.domain.LogHead;
import io.vidocq.tools.dashboard.domain.OccupancyCalculator;
import io.vidocq.tools.dashboard.forge.ForgeApi;
import io.vidocq.tools.dashboard.forge.ForgeJob;
import io.vidocq.tools.dashboard.forge.ForgeNotFoundException;
import io.vidocq.tools.dashboard.forge.ForgeRun;
import io.vidocq.tools.dashboard.forge.ForgeRunner;
import io.vidocq.tools.dashboard.forge.ForgeUnavailableException;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.RunnerView;
import io.vidocq.tools.dashboard.store.JobExecution;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.QueueSample;
import io.vidocq.tools.dashboard.store.QueueSampleRepository;
import io.vidocq.tools.dashboard.store.RunnerRow;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.store.RunnerStatusChange;
import io.vidocq.tools.dashboard.store.RunnerStatusChangeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The runners loop (15 s): the runners and their status changes, the job executions with the runner the head of their
 * log names, the queue per minute, then the live snapshot of the runners.
 * <p>
 * Everything it needs to carry from one tick to the next is in the database: an execution whose runner is still
 * unknown is retried from the open executions, so a restart loses nothing.
 */
@ApplicationScoped
public class RunnersLoop {

    private static final System.Logger LOG = System.getLogger(RunnersLoop.class.getName());
    private static final String RUNNING = "running";
    private static final Duration CAPACITY_WINDOW = Duration.ofDays(7);
    private static final int RUN_CACHE_MAX = 512;

    private final ForgeApi forge;
    private final JobExecutionRepository jobs;
    private final RunnerRowRepository runners;
    private final RunnerStatusChangeRepository changes;
    private final QueueSampleRepository queue;
    private final LiveState live;
    private final Clock clock;
    /** Runs by id: a run's jobs all start within minutes, and a run never changes the fields kept. */
    private final Map<Long, ForgeRun> runs = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ForgeRun> eldest) {
            return size() > RUN_CACHE_MAX;
        }
    };

    @Inject
    public RunnersLoop(ForgeApi forge, JobExecutionRepository jobs, RunnerRowRepository runners,
                       RunnerStatusChangeRepository changes, QueueSampleRepository queue, LiveState live, Clock clock) {
        this.forge = forge;
        this.jobs = jobs;
        this.runners = runners;
        this.changes = changes;
        this.queue = queue;
        this.live = live;
        this.clock = clock;
    }

    /**
     * At start, before any pass: the runners the database holds, with the executions still open, so that a restart
     * while CodeFloe is down shows the last state (marked stale) rather than nothing. A snapshot a pass already filled
     * is left alone.
     */
    public synchronized void restore(String org) {
        List<RunnerRow> rows = runners.findByOrg(org);
        if (rows.isEmpty()) {
            return;
        }
        List<RunnerView> views = runnerViews(org, rows, clock.instant());
        live.update(org, s -> s.runners().isEmpty() ? s.withRunners(views, s.waiting(), s.updatedAt()) : s,
                new ChangeEvent.RunnersChanged(org));
    }

    /** One complete pass for {@code org}. A failure of CodeFloe propagates: the scheduler backs off. */
    public synchronized void tick(String org) {
        Instant now = clock.instant();
        Map<String, RunnerRow> byUuid = refreshRunners(org, now);

        List<ForgeJob> active = forge.listActiveJobs(org);
        Map<Long, JobExecution> open = jobs.findByOrgAndStoppedAtNull(org).stream()
                .collect(Collectors.toMap(JobExecution::getTaskId, Function.identity()));
        RunnerDiffResult diff = RunnerDiff.diff(open.keySet(), active);

        for (ForgeJob job : diff.newRunning()) {
            String repo = JobResolver.repoOf(job.htmlUrl()).orElse("?");
            jobs.save(JobResolver.resolve(org, job, run(org, repo, job.runId()), repo, logHead(job), byUuid, now));
        }
        retryUnresolved(active, open, byUuid);
        for (long taskId : diff.finishedTaskIds()) {
            close(open.get(taskId), byUuid, now);
        }
        sampleQueue(org, now, diff.waiting().size(), (int) active.stream().filter(j -> RUNNING.equals(j.status())).count());

        List<RunnerView> views = runnerViews(org, byUuid.values(), now);
        List<JobView> waiting = diff.waiting().stream().map(RunnersLoop::waitingView).toList();
        live.update(org, s -> s.withRunners(views, waiting, now), new ChangeEvent.RunnersChanged(org));
    }

    /** Upserts the runners; a status that differs from the last one recorded is a change. */
    private Map<String, RunnerRow> refreshRunners(String org, Instant now) {
        Map<String, RunnerRow> byUuid = new LinkedHashMap<>();
        for (ForgeRunner r : forge.listRunners(org)) {
            RunnerRow row = runners.findById(r.id()).orElseGet(RunnerRow::new);
            String previous = row.getLastStatus();
            row.setId(r.id());
            row.setOrg(org);
            row.setUuid(r.uuid());
            row.setName(r.name());
            row.setLabels(String.join(",", r.labels()));
            row.setVersion(r.version());
            row.setLastStatus(r.status());
            row.setLastSeenAt(now);
            runners.save(row);
            if (!Objects.equals(previous, r.status())) {
                changes.save(new RunnerStatusChange(r.id(), org, r.status(), now));
            }
            byUuid.put(r.uuid(), row);
        }
        return byUuid;
    }

    /** The open executions still running whose runner is unknown: their log may have started since. */
    private void retryUnresolved(List<ForgeJob> active, Map<Long, JobExecution> open, Map<String, RunnerRow> byUuid) {
        for (ForgeJob job : active) {
            JobExecution e = open.get(job.taskId());
            if (e != null && e.getRunnerId() == null && RUNNING.equals(job.status())
                    && JobResolver.applyHead(e, logHead(job), byUuid)) {
                jobs.save(e);
            }
        }
    }

    /**
     * A task gone from the active jobs: its final status and stop from the job's detail, else now. A detail CodeFloe
     * cannot serve (it answers 500 for some jobs) closes it the same way: left open, it would freeze the pass of the
     * whole organisation, or count as running for ever.
     */
    private void close(JobExecution e, Map<String, RunnerRow> byUuid, Instant now) {
        try {
            ForgeJob detail = forge.getJob(e.getOrg(), e.getRepo(), e.getJobId());
            e.setStatus(detail.status());
            e.setStoppedAt(detail.stoppedAt() != null ? detail.stoppedAt() : now);
            if (e.getRunnerId() == null) {
                JobResolver.applyHead(e, logHead(detail), byUuid);
            }
        } catch (ForgeNotFoundException | ForgeUnavailableException noDetail) {
            if (noDetail instanceof ForgeUnavailableException) {
                LOG.log(System.Logger.Level.INFO, "Job " + e.getJobId() + " closed without its detail: "
                        + noDetail.getMessage());
            }
            e.setStoppedAt(now);
            e.setStatus("unknown");
        }
        if (e.getStoppedAt().isBefore(e.getStartedAt())) {
            e.setStoppedAt(e.getStartedAt());
        }
        jobs.save(e);
    }

    /**
     * The head of the job's log, empty when CodeFloe cannot serve it now (it answers 500 for some logs): the job
     * stays without runner and the next tick reads it again. A rate limit still stops the pass.
     */
    private Optional<LogHead> logHead(ForgeJob job) {
        try {
            return forge.getJobLogHead(job).flatMap(LogHead::parse);
        } catch (ForgeUnavailableException unreadable) {
            LOG.log(System.Logger.Level.DEBUG, "Log head of job " + job.id() + " unreadable, retried next tick: "
                    + unreadable.getMessage());
            return Optional.empty();
        }
    }

    private ForgeRun run(String org, String repo, long runId) {
        ForgeRun cached = runs.get(runId);
        if (cached != null) {
            return cached;
        }
        try {
            ForgeRun run = forge.getRun(org, repo, runId);
            runs.put(runId, run);
            return run;
        } catch (ForgeNotFoundException gone) {
            return null;
        }
    }

    /** One sample per org and minute, holding the maxima seen during the minute. */
    private void sampleQueue(String org, Instant now, int waiting, int running) {
        Instant minute = now.truncatedTo(ChronoUnit.MINUTES);
        QueueSample sample = queue.findByOrgAndMinute(org, minute)
                .orElseGet(() -> new QueueSample(org, minute, 0, 0));
        if (sample.getId() != null && waiting <= sample.getWaiting() && running <= sample.getRunning()) {
            return;
        }
        sample.setWaiting(Math.max(waiting, sample.getWaiting()));
        sample.setRunning(Math.max(running, sample.getRunning()));
        queue.save(sample);
    }

    private List<RunnerView> runnerViews(String org, Iterable<RunnerRow> rows, Instant now) {
        Map<Long, String> names = new HashMap<>();
        rows.forEach(r -> names.put(r.getId(), r.getName()));
        Map<Long, List<JobView>> running = new HashMap<>();
        for (JobExecution e : jobs.findByOrgAndStoppedAtNull(org)) {
            if (e.getRunnerId() != null) {
                running.computeIfAbsent(e.getRunnerId(), id -> new ArrayList<>())
                        .add(runningView(e, names.get(e.getRunnerId())));
            }
        }
        List<RunnerView> views = new ArrayList<>();
        for (RunnerRow r : rows) {
            List<JobView> held = running.getOrDefault(r.getId(), List.of()).stream()
                    .sorted((a, b) -> a.startedAt().compareTo(b.startedAt())).toList();
            views.add(new RunnerView(r.getId(), r.getName(), r.getLastStatus(), labels(r), capacity(r, now), held));
        }
        return views;
    }

    /** The capacity override, else the most jobs the runner held at once over 7 days. */
    private int capacity(RunnerRow r, Instant now) {
        if (r.getCapacityOverride() != null && r.getCapacityOverride() > 0) {
            return r.getCapacityOverride();
        }
        List<Interval> intervals = jobs.findByRunnerIdAndStartedAtGreaterThanOrderByStartedAtDesc(r.getId(),
                        now.minus(CAPACITY_WINDOW)).stream()
                .map(e -> new Interval(r.getId(), e.getStartedAt(), e.getStoppedAt()))
                .toList();
        return OccupancyCalculator.observedCapacity(intervals);
    }

    private static List<String> labels(RunnerRow r) {
        return r.getLabels() == null || r.getLabels().isBlank() ? List.of() : Arrays.asList(r.getLabels().split(","));
    }

    private static JobView runningView(JobExecution e, String runnerName) {
        return new JobView(e.getTaskId(), e.getJobId(), e.getRepo(), e.getPrNumber(), e.getRef(), e.getJobName(),
                e.getStatus(), e.getStartedAt(), e.getHtmlUrl(), e.getRunnerId(), runnerName);
    }

    private static JobView waitingView(ForgeJob j) {
        return new JobView(j.taskId(), j.id(), JobResolver.repoOf(j.htmlUrl()).orElse("?"), null, null, j.name(),
                j.status(), null, j.htmlUrl(), null, null);
    }
}
