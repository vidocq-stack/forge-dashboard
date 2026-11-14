package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.LoopHealth;
import io.vidocq.tools.dashboard.store.StatsQueries;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Starts the collector loops once the application is up, each on its own scheduler and virtual thread, and stops
 * them at shutdown. Every pass runs over the active organisations; {@code dashboard.collect.enabled=false} starts
 * none of them (the tests).
 * <p>
 * The repositories refresh and the commits loop both write {@code repo} rows, the first one from what CodeFloe says,
 * the second one its cursor: they take turns, so that one never saves a row the other read before its write.
 */
@ApplicationScoped
public class CollectorScheduler {

    private static final System.Logger LOG = System.getLogger(CollectorScheduler.class.getName());
    private static final Duration JOB_RETENTION = Duration.ofDays(180);
    private static final Duration QUEUE_RETENTION = Duration.ofDays(30);
    private static final Duration AFTER_ERROR = Duration.ofMinutes(15);

    private final OrgRegistry registry;
    private final RunnersLoop runners;
    private final PullsLoop pulls;
    private final SonarLoop sonar;
    private final CommitsLoop commits;
    private final StatsQueries stats;
    private final ChangeBus bus;
    private final Clock clock;
    private final Object repoRows = new Object();
    private final List<LoopRunner> loops = new CopyOnWriteArrayList<>();
    private final List<ScheduledExecutorService> executors = new CopyOnWriteArrayList<>();

    @Inject
    @ConfigProperty(name = "dashboard.collect.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    @ConfigProperty(name = "dashboard.initial.orgs", defaultValue = "Vidocq,VidocqTools")
    String initialOrgs;

    @Inject
    @ConfigProperty(name = "dashboard.collect.runners.seconds", defaultValue = "15")
    long runnersSeconds;

    @Inject
    @ConfigProperty(name = "dashboard.collect.pulls.seconds", defaultValue = "90")
    long pullsSeconds;

    @Inject
    @ConfigProperty(name = "dashboard.collect.repos.seconds", defaultValue = "300")
    long reposSeconds;

    @Inject
    @ConfigProperty(name = "dashboard.collect.sonar.seconds", defaultValue = "900")
    long sonarSeconds;

    @Inject
    @ConfigProperty(name = "dashboard.collect.commits.seconds", defaultValue = "3600")
    long commitsSeconds;

    /** While a back-fill is left, the commits loop comes back after this delay instead of its cadence. */
    @Inject
    @ConfigProperty(name = "dashboard.collect.commits.backfill.seconds", defaultValue = "60")
    long backfillSeconds;

    @Inject
    @ConfigProperty(name = "dashboard.collect.purge.seconds", defaultValue = "86400")
    long purgeSeconds;

    @Inject
    public CollectorScheduler(OrgRegistry registry, RunnersLoop runners, PullsLoop pulls, SonarLoop sonar,
                              CommitsLoop commits, StatsQueries stats, ChangeBus bus, Clock clock) {
        this.registry = registry;
        this.runners = runners;
        this.pulls = pulls;
        this.sonar = sonar;
        this.commits = commits;
        this.stats = stats;
        this.bus = bus;
        this.clock = clock;
    }

    public void onStart(@Observes Startup event) {
        if (!enabled) {
            LOG.log(System.Logger.Level.INFO, "Collector loops disabled (dashboard.collect.enabled=false)");
            return;
        }
        registry.seedIfEmpty(Arrays.asList(initialOrgs.split(",")));
        Supplier<List<String>> orgs = registry::activeOrgs;
        Duration backfill = Duration.ofSeconds(backfillSeconds);

        schedule("runners", runnersSeconds, Duration.ZERO, LoopRunner.eachOrg(orgs, runners::tick), UnaryOperator.identity());
        schedule("repos", reposSeconds, Duration.ZERO,
                LoopRunner.eachOrg(orgs, org -> inTurn(() -> pulls.refreshRepos(org))), UnaryOperator.identity());
        schedule("pulls", pullsSeconds, Duration.ofSeconds(10), LoopRunner.eachOrg(orgs, pulls::tick),
                UnaryOperator.identity());
        schedule("sonar", sonarSeconds, Duration.ofSeconds(30), LoopRunner.eachOrg(orgs, sonar::tick),
                UnaryOperator.identity());
        schedule("commits", commitsSeconds, Duration.ofSeconds(45),
                LoopRunner.eachOrg(orgs, org -> inTurn(() -> commits.tick(org))),
                next -> next.equals(Duration.ofSeconds(commitsSeconds)) && commits.backfillPending()
                        ? backfill : next);
        schedule("purge", purgeSeconds, Duration.ofMinutes(5), this::purge, UnaryOperator.identity());
        LOG.log(System.Logger.Level.INFO, "Collector loops started for " + registry.activeOrgs());
    }

    public void onStop(@Observes Shutdown event) {
        executors.forEach(ScheduledExecutorService::shutdownNow);
        executors.clear();
    }

    public List<LoopHealth> health() {
        return loops.stream().map(LoopRunner::health).toList();
    }

    private void schedule(String name, long cadenceSeconds, Duration firstDelay, Runnable tick,
                          UnaryOperator<Duration> adjust) {
        Duration cadence = Duration.ofSeconds(cadenceSeconds);
        var runner = new LoopRunner(name, cadence, tick, clock, h -> bus.publish(new ChangeEvent.HealthChanged()));
        var executor = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("collect-" + name).factory());
        loops.add(runner);
        executors.add(executor);
        submit(executor, () -> pass(executor, runner, adjust), firstDelay);
    }

    private void pass(ScheduledExecutorService executor, LoopRunner runner, UnaryOperator<Duration> adjust) {
        Duration next;
        try {
            next = adjust.apply(runner.runOnce());
        } catch (Throwable t) {   // an Error must not end the loop for good
            LOG.log(System.Logger.Level.ERROR, "Collector loop " + runner.name() + " crashed", t);
            next = AFTER_ERROR;
        }
        submit(executor, () -> pass(executor, runner, adjust), next);
    }

    private static void submit(ScheduledExecutorService executor, Runnable task, Duration delay) {
        if (executor.isShutdown()) {
            return;
        }
        try {
            executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopping) {
            // shut down meanwhile
        }
    }

    private void inTurn(Runnable writesRepoRows) {
        synchronized (repoRows) {
            writesRepoRows.run();
        }
    }

    private void purge() {
        Instant now = clock.instant();
        int removed = stats.purge(now.minus(JOB_RETENTION), now.minus(QUEUE_RETENTION));
        LOG.log(System.Logger.Level.INFO, "Purge: " + removed + " rows removed");
    }
}
