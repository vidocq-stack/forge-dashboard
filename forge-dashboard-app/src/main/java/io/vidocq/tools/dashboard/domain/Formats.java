package io.vidocq.tools.dashboard.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import io.vidocq.tools.dashboard.i18n.Messages;

/** Short renderings of counts, ages and durations, for dense tables; ages and durations in the given language. */
public final class Formats {

    private Formats() {
    }

    /** {@code 2077}, {@code 41.8k}, {@code 1.3M}: as is below 10 000, then one decimal. */
    public static String compact(long n) {
        if (Math.abs(n) < 10_000) {
            return Long.toString(n);
        }
        double k = Math.round(n / 100.0) / 10.0;
        if (Math.abs(k) < 1_000) {
            return String.format(Locale.ROOT, "%.1fk", k);
        }
        return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
    }

    /**
     * {@code 8 s ago}, {@code il y a 3 min}, {@code vor 2 h}, {@code 4 d geleden}: seconds, minutes, hours or days; a
     * future instant is 0 s.
     */
    public static String relative(Instant t, Instant now, Messages m) {
        long s = Math.max(0, Duration.between(t, now).toSeconds());
        String age;
        if (s < 60) {
            age = m.text("duration.seconds", s);
        } else if (s < 3_600) {
            age = m.text("duration.minutes", s / 60);
        } else if (s < 86_400) {
            age = m.text("duration.hours", s / 3_600);
        } else {
            age = m.text("duration.days", s / 86_400);
        }
        return m.text("duration.ago", age);
    }

    /** {@code 42 s}, {@code 3 min 05 s}, {@code 1 h 12 min}; a negative duration is 0 s. */
    public static String duration(Duration d, Messages m) {
        long s = Math.max(0, d.toSeconds());
        if (s < 60) {
            return m.text("duration.seconds", s);
        }
        if (s < 3_600) {
            return m.text("duration.minutesSeconds", s / 60, twoDigits(s % 60));
        }
        return m.text("duration.hoursMinutes", s / 3_600, twoDigits(s % 3_600 / 60));
    }

    private static String twoDigits(long n) {
        return String.format(Locale.ROOT, "%02d", n);
    }
}
