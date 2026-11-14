package io.vidocq.tools.dashboard.forge.cyrano;

import io.vidocq.tools.dashboard.forge.ForgeNotFoundException;
import io.vidocq.tools.dashboard.forge.ForgeUnavailableException;
import io.vidocq.tools.dashboard.forge.RateLimitedException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForgeErrorsTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void successPasses() {
        assertDoesNotThrow(() -> ForgeErrors.check(200, null, "repos", NOW));
        assertDoesNotThrow(() -> ForgeErrors.check(206, null, "log", NOW));
    }

    @Test
    void notFoundAndEmptyRepoAreNotFound() {
        assertThrows(ForgeNotFoundException.class, () -> ForgeErrors.check(404, null, "repo x", NOW));
        assertThrows(ForgeNotFoundException.class, () -> ForgeErrors.check(409, null, "empty repo", NOW));
    }

    @Test
    void rateLimitCarriesRetryAfter() {
        var e = assertThrows(RateLimitedException.class, () -> ForgeErrors.check(429, "120", "repos", NOW));
        assertEquals(Duration.ofSeconds(120), e.retryAfter());
    }

    @Test
    void rateLimitWithoutOrWithBadRetryAfterWaitsAMinute() {
        assertEquals(Duration.ofSeconds(60),
                assertThrows(RateLimitedException.class, () -> ForgeErrors.check(429, null, "x", NOW)).retryAfter());
        assertEquals(Duration.ofSeconds(60),
                assertThrows(RateLimitedException.class, () -> ForgeErrors.check(429, "soon", "x", NOW)).retryAfter());
    }

    @Test
    void rateLimitWithAnHttpDate() {
        var e = assertThrows(RateLimitedException.class,
                () -> ForgeErrors.check(429, "Sat, 14 Nov 2026 12:05:00 GMT", "x", NOW));
        assertEquals(Duration.ofMinutes(5), e.retryAfter());
    }

    @Test
    void otherErrorsAreUnavailable() {
        var e = assertThrows(ForgeUnavailableException.class, () -> ForgeErrors.check(502, null, "runners", NOW));
        assertEquals("CodeFloe answered 502 for runners", e.getMessage());
        assertThrows(ForgeUnavailableException.class, () -> ForgeErrors.check(403, null, "runners", NOW));
    }
}
