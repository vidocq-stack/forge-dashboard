package io.vidocq.tools.dashboard.forge.cyrano;

import io.vidocq.tools.dashboard.forge.ForgeNotFoundException;
import io.vidocq.tools.dashboard.forge.ForgeUnavailableException;
import io.vidocq.tools.dashboard.forge.RateLimitedException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Turns a Forgejo HTTP status into the port's exceptions. */
final class ForgeErrors {

    static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(60);

    private ForgeErrors() {
    }

    /**
     * Returns on a 2xx; else throws {@link ForgeNotFoundException} (404, 409), {@link RateLimitedException} (429) or
     * {@link ForgeUnavailableException}.
     */
    static void check(int status, String retryAfter, String what, Instant now) {
        if (status >= 200 && status < 300) {
            return;
        }
        switch (status) {
            case 404, 409 -> throw new ForgeNotFoundException(what + " (" + status + ")");
            case 429 -> throw new RateLimitedException(retryAfter(retryAfter, now));
            default -> throw new ForgeUnavailableException("CodeFloe answered " + status + " for " + what, null);
        }
    }

    /** {@code Retry-After} in seconds or as an HTTP date; 60 s when absent or unreadable. */
    static Duration retryAfter(String header, Instant now) {
        if (header == null || header.isBlank()) {
            return DEFAULT_RETRY_AFTER;
        }
        String value = header.strip();
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
        } catch (NumberFormatException notSeconds) {
            try {
                Duration d = Duration.between(now, ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
                return d.isNegative() ? Duration.ZERO : d;
            } catch (DateTimeParseException notADate) {
                return DEFAULT_RETRY_AFTER;
            }
        }
    }
}
