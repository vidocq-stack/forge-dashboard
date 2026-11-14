package io.vidocq.tools.dashboard.forge;

import java.time.Duration;

/** The forge answered 429: wait {@link #retryAfter()} before the next call. */
public final class RateLimitedException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super("CodeFloe rate limit: retry after " + retryAfter.toSeconds() + " s");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
