package io.vidocq.tools.dashboard.domain;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Where a running job stands against its usual duration: how far along it is (at most 99 %: a job past its usual
 * duration is late, not done) and the time {@code left}, negative once it runs late.
 */
public record JobEta(int percent, Duration left) {

    /** Nothing without a usual duration (fewer than 3 successful runs, see {@link UsualDurations}). */
    public static Optional<JobEta> of(Duration elapsed, Duration usual) {
        if (elapsed == null || usual == null || !usual.isPositive()) {
            return Optional.empty();
        }
        Duration done = elapsed.isNegative() ? Duration.ZERO : elapsed;
        int percent = (int) Math.min(99, done.toMillis() * 100 / usual.toMillis());
        return Optional.of(new JobEta(percent, usual.minus(done)));
    }

    /** Past its usual duration. */
    public boolean overrun() {
        return left.isNegative();
    }

    /** A build's estimate: its running job with the most time left (the latest of the late ones when all are). */
    public static Optional<JobEta> longest(Collection<JobEta> etas) {
        return etas.stream().max(Comparator.comparing(JobEta::left));
    }
}
