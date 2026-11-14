package io.vidocq.tools.dashboard.live;

import java.time.Duration;
import java.time.Instant;

/** The freshness of a collector loop, for the footer indicator. */
public record LoopHealth(String loop, Instant lastSuccess, Instant lastFailure, String lastError, Duration cadence) {

    /** No success yet, or none for more than three cadences. */
    public boolean stale(Instant now) {
        return lastSuccess == null || lastSuccess.isBefore(now.minus(cadence.multipliedBy(3)));
    }
}
