package io.vidocq.tools.dashboard.domain;

import io.vidocq.tools.dashboard.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatsTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final Messages FR = Messages.forLocale(Locale.FRENCH);

    @Test
    void compactAndRelative() {
        assertEquals("2077", Formats.compact(2077));
        assertEquals("41.8k", Formats.compact(41_814));
        assertEquals("1.3M", Formats.compact(1_250_000));
        assertEquals("il y a 8 s", Formats.relative(NOW.minusSeconds(8), NOW, FR));
        assertEquals("il y a 2 h", Formats.relative(NOW.minus(Duration.ofHours(2)), NOW, FR));
        assertEquals("3 min 05 s", Formats.duration(Duration.ofSeconds(185), FR));
    }

    /** The time left, rounded up: to the minute, or to 10 s under a minute; late, by how much. */
    @Test
    void etaLeftOrLate() {
        assertEquals("62 % · reste ~3 min", Formats.eta(new JobEta(62, Duration.ofSeconds(150)), FR));
        assertEquals("95 % · reste ~40 s", Formats.eta(new JobEta(95, Duration.ofSeconds(33)), FR));
        assertEquals("99 % · reste ~10 s", Formats.eta(new JobEta(99, Duration.ZERO), FR));
        assertEquals("+2 min sur l'habitude", Formats.eta(new JobEta(99, Duration.ofSeconds(-90)), FR));
        assertEquals("10 % · reste ~1 h 05 min", Formats.eta(new JobEta(10, Duration.ofSeconds(3_841)), FR));
        Messages en = Messages.forLocale(Locale.ENGLISH);
        assertEquals("62% · ~3 min left", Formats.eta(new JobEta(62, Duration.ofSeconds(150)), en));
        assertEquals("2 min over the usual", Formats.eta(new JobEta(99, Duration.ofSeconds(-90)), en));
    }

    @Test
    void compactBoundaries() {
        assertEquals("9999", Formats.compact(9_999));
        assertEquals("10.0k", Formats.compact(10_000));
        assertEquals("1.0M", Formats.compact(999_999));
    }

    @Test
    void relativeUnits() {
        assertEquals("il y a 3 min", Formats.relative(NOW.minusSeconds(3 * 60 + 20), NOW, FR));
        assertEquals("il y a 4 j", Formats.relative(NOW.minus(Duration.ofDays(4)), NOW, FR));
        assertEquals("il y a 0 s", Formats.relative(NOW.plusSeconds(5), NOW, FR));
    }

    @Test
    void durationUnits() {
        assertEquals("42 s", Formats.duration(Duration.ofSeconds(42), FR));
        assertEquals("1 h 12 min", Formats.duration(Duration.ofMinutes(72), FR));
        assertEquals("0 s", Formats.duration(Duration.ofSeconds(-3), FR));
    }

    @Test
    void relativeInEachLanguage() {
        Instant eightSeconds = NOW.minusSeconds(8);
        assertEquals("8 s ago", Formats.relative(eightSeconds, NOW, Messages.forLocale(Locale.ENGLISH)));
        assertEquals("vor 8 s", Formats.relative(eightSeconds, NOW, Messages.forLocale(Locale.GERMAN)));
        assertEquals("8 s geleden", Formats.relative(eightSeconds, NOW, Messages.forLocale(Locale.forLanguageTag("nl"))));
        assertEquals("il y a 8 s", Formats.relative(eightSeconds, NOW, FR));
    }

    @Test
    void daysAreShortInEachLanguage() {
        Instant fifteenDays = NOW.minus(Duration.ofDays(15));
        assertEquals("15 d ago", Formats.relative(fifteenDays, NOW, Messages.forLocale(Locale.ENGLISH)));
        assertEquals("vor 15 T.", Formats.relative(fifteenDays, NOW, Messages.forLocale(Locale.GERMAN)));
        assertEquals("15 d geleden", Formats.relative(fifteenDays, NOW, Messages.forLocale(Locale.forLanguageTag("nl"))));
    }

    @Test
    void durationKeepsTwoDigitsInEachLanguage() {
        assertEquals("3 min 05 s", Formats.duration(Duration.ofSeconds(185), Messages.forLocale(Locale.GERMAN)));
        assertEquals("1 h 02 min", Formats.duration(Duration.ofMinutes(62), Messages.forLocale(Locale.ENGLISH)));
    }
}
