package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.live.LoopHealth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The freshness badge of the header: the age of the latest collector pass, or "périmé" when a loop is late; the
 * details (its tooltip) say which loop and why. A loop that has not run yet is not late, unless it failed.
 */
public record Freshness(String text, boolean stale, String details) {

    public static Freshness of(List<LoopHealth> loops, Instant now) {
        if (loops.isEmpty()) {
            return new Freshness("collecte désactivée", false, "");
        }
        List<LoopHealth> late = loops.stream().filter(h -> late(h, now)).toList();
        if (!late.isEmpty()) {
            return new Freshness("périmé", true,
                    late.stream().map(h -> describe(h, now)).collect(Collectors.joining("\n")));
        }
        Instant latest = loops.stream().map(LoopHealth::lastSuccess).filter(Objects::nonNull)
                .max(Instant::compareTo).orElse(null);
        if (latest == null) {
            return new Freshness("collecte en cours…", false, "");
        }
        return new Freshness("frais il y a " + age(Duration.between(latest, now)), false, "");
    }

    private static boolean late(LoopHealth h, Instant now) {
        return h.lastSuccess() != null ? h.stale(now) : h.lastFailure() != null;
    }

    private static String describe(LoopHealth h, Instant now) {
        String success = h.lastSuccess() == null ? "jamais réussie"
                : "dernière réussite il y a " + age(Duration.between(h.lastSuccess(), now));
        return h.loop() + " : " + success + (h.lastError() == null ? "" : " — " + h.lastError());
    }

    static String age(Duration d) {
        long s = Math.max(0, d.toSeconds());
        if (s < 60) {
            return s + " s";
        }
        if (s < 3600) {
            return s / 60 + " min";
        }
        if (s < 172_800) {
            return s / 3600 + " h";
        }
        return s / 86_400 + " j";
    }
}
