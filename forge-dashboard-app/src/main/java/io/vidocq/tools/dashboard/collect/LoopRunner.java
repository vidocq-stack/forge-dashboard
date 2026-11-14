package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.RateLimitedException;
import io.vidocq.tools.dashboard.live.LoopHealth;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A named collector loop and its health. {@link #runOnce()} runs one tick and says when to run the next one: the
 * cadence after a success, a growing delay after failures (see {@link Backoff}). A failed tick leaves the live state
 * as the last success left it; only the health tells it is getting old.
 */
final class LoopRunner {

    private static final System.Logger LOG = System.getLogger(LoopRunner.class.getName());
    private static final int MAX_ERROR_LENGTH = 500;

    private final String name;
    private final Duration cadence;
    private final Runnable tick;
    private final Clock clock;
    private final Consumer<LoopHealth> onHealth;
    private int failures;
    private volatile LoopHealth health;

    LoopRunner(String name, Duration cadence, Runnable tick, Clock clock, Consumer<LoopHealth> onHealth) {
        this.name = name;
        this.cadence = cadence;
        this.tick = tick;
        this.clock = clock;
        this.onHealth = onHealth;
        this.health = new LoopHealth(name, null, null, null, cadence);
    }

    /** Runs the tick, updates the health, and returns the delay before the next pass. */
    synchronized Duration runOnce() {
        Duration next;
        long started = System.nanoTime();
        try {
            tick.run();
            if (failures > 0 || health.lastSuccess() == null) {
                LOG.log(System.Logger.Level.INFO, "Collector loop " + name + " ok in "
                        + Duration.ofNanos(System.nanoTime() - started).toMillis() + " ms"
                        + (failures > 0 ? " after " + failures + " failure(s)" : ""));
            }
            failures = 0;
            health = new LoopHealth(name, clock.instant(), health.lastFailure(), health.lastError(), cadence);
            next = cadence;
        } catch (RuntimeException e) {
            failures++;
            Optional<Duration> retryAfter = e instanceof RateLimitedException r
                    ? Optional.of(r.retryAfter()) : Optional.empty();
            next = Backoff.next(cadence, failures, retryAfter);
            health = new LoopHealth(name, health.lastSuccess(), clock.instant(), describe(e), cadence);
            LOG.log(System.Logger.Level.WARNING, "Collector loop " + name + " failed (" + failures
                    + " in a row), next pass in " + next.toSeconds() + " s: " + describe(e));
        }
        onHealth.accept(health);
        return next;
    }

    LoopHealth health() {
        return health;
    }

    /** Whether the last pass failed. */
    synchronized boolean failing() {
        return failures > 0;
    }

    String name() {
        return name;
    }

    /**
     * A tick that runs {@code perOrg} for each organisation. A failure on one is logged and the others still run;
     * the tick then fails with the first one, the others suppressed. A rate limit stops the sweep at once: the
     * limit is the token's, the next organisation would hit it too.
     */
    static Runnable eachOrg(Supplier<List<String>> orgs, Consumer<String> perOrg) {
        return () -> {
            RuntimeException first = null;
            for (String org : orgs.get()) {
                try {
                    perOrg.accept(org);
                } catch (RateLimitedException limited) {
                    throw limited;
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING, "Collector pass failed for " + org, e);
                    var failure = new IllegalStateException(org + ": " + describe(e), e);
                    if (first == null) {
                        first = failure;
                    } else {
                        first.addSuppressed(failure);
                    }
                }
            }
            if (first != null) {
                throw first;
            }
        };
    }

    private static String describe(RuntimeException e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }
}
