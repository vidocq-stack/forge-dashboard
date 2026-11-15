package io.vidocq.tools.dashboard.domain;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** A job's usual duration: the median of its last successful runs, once there are enough of them. */
public final class UsualDurations {

    /** How many of a job's last successful runs make its usual duration. */
    public static final int LAST_RUNS = 10;

    /** Below this, one cold cache or one flaky step would set the estimate. */
    public static final int MIN_RUNS = 3;

    /** How far back the runs are read: a job not run for a month starts over. */
    public static final Duration WINDOW = Duration.ofDays(30);

    private UsualDurations() {
    }

    public static Optional<Duration> median(List<Duration> durations) {
        if (durations.size() < MIN_RUNS) {
            return Optional.empty();
        }
        List<Duration> sorted = durations.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return Optional.of(sorted.size() % 2 == 1 ? sorted.get(middle)
                : sorted.get(middle - 1).plus(sorted.get(middle)).dividedBy(2));
    }
}
