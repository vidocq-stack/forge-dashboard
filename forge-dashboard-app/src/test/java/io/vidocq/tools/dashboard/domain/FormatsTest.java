package io.vidocq.tools.dashboard.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatsTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void compactAndRelative() {
        assertEquals("2077", Formats.compact(2077));
        assertEquals("41.8k", Formats.compact(41_814));
        assertEquals("1.3M", Formats.compact(1_250_000));
        assertEquals("il y a 8 s", Formats.relative(NOW.minusSeconds(8), NOW));
        assertEquals("il y a 2 h", Formats.relative(NOW.minus(Duration.ofHours(2)), NOW));
        assertEquals("3 min 05 s", Formats.duration(Duration.ofSeconds(185)));
    }

    @Test
    void compactBoundaries() {
        assertEquals("9999", Formats.compact(9_999));
        assertEquals("10.0k", Formats.compact(10_000));
        assertEquals("1.0M", Formats.compact(999_999));
    }

    @Test
    void relativeUnits() {
        assertEquals("il y a 3 min", Formats.relative(NOW.minusSeconds(3 * 60 + 20), NOW));
        assertEquals("il y a 4 j", Formats.relative(NOW.minus(Duration.ofDays(4)), NOW));
        assertEquals("il y a 0 s", Formats.relative(NOW.plusSeconds(5), NOW));
    }

    @Test
    void durationUnits() {
        assertEquals("42 s", Formats.duration(Duration.ofSeconds(42)));
        assertEquals("1 h 12 min", Formats.duration(Duration.ofMinutes(72)));
        assertEquals("0 s", Formats.duration(Duration.ofSeconds(-3)));
    }
}
