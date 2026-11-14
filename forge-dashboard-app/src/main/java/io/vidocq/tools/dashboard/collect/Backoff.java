package io.vidocq.tools.dashboard.collect;

import java.time.Duration;
import java.util.Optional;

/** The delay before the next pass of a loop that failed {@code consecutiveFailures} times in a row. */
final class Backoff {

    static final Duration CAP = Duration.ofMinutes(15);

    private Backoff() {
    }

    /**
     * {@code base × 2^failures}, capped at 15 min, but never sooner than {@code base}: a loop slower than the cap
     * keeps its cadence. A {@code Retry-After} of the forge wins when it is longer.
     */
    static Duration next(Duration base, int consecutiveFailures, Optional<Duration> retryAfter) {
        Duration cap = base.compareTo(CAP) > 0 ? base : CAP;
        Duration delay = base;
        for (int i = 0; i < consecutiveFailures && delay.compareTo(cap) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        Duration capped = delay.compareTo(cap) > 0 ? cap : delay;
        return retryAfter.filter(r -> r.compareTo(capped) > 0).orElse(capped);
    }
}
