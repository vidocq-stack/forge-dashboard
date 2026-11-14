package io.vidocq.tools.dashboard.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Short French renderings of counts, ages and durations, for dense tables. */
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

    /** {@code il y a 8 s}, {@code il y a 3 min}, {@code il y a 2 h}, {@code il y a 4 j}; a future instant is 0 s. */
    public static String relative(Instant t, Instant now) {
        long s = Math.max(0, Duration.between(t, now).toSeconds());
        if (s < 60) {
            return "il y a " + s + " s";
        }
        if (s < 3_600) {
            return "il y a " + s / 60 + " min";
        }
        if (s < 86_400) {
            return "il y a " + s / 3_600 + " h";
        }
        return "il y a " + s / 86_400 + " j";
    }

    /** {@code 42 s}, {@code 3 min 05 s}, {@code 1 h 12 min}; a negative duration is 0 s. */
    public static String duration(Duration d) {
        long s = Math.max(0, d.toSeconds());
        if (s < 60) {
            return s + " s";
        }
        if (s < 3_600) {
            return String.format(Locale.ROOT, "%d min %02d s", s / 60, s % 60);
        }
        return String.format(Locale.ROOT, "%d h %02d min", s / 3_600, s % 3_600 / 60);
    }
}
