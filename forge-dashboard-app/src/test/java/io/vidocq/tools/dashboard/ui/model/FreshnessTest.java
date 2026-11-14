package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.live.LoopHealth;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreshnessTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    private static LoopHealth ok(String loop, Duration ago, Duration cadence) {
        return new LoopHealth(loop, NOW.minus(ago), null, null, cadence);
    }

    @Test
    void noLoopMeansTheCollectionIsOff() {
        Freshness f = Freshness.of(List.of(), NOW);
        assertEquals("collecte désactivée", f.text());
        assertFalse(f.stale());
    }

    @Test
    void freshShowsTheAgeOfTheLatestPass() {
        Freshness f = Freshness.of(List.of(
                ok("runners", Duration.ofSeconds(4), Duration.ofSeconds(15)),
                ok("pulls", Duration.ofSeconds(50), Duration.ofSeconds(90))), NOW);
        assertEquals("frais il y a 4 s", f.text());
        assertFalse(f.stale());
    }

    @Test
    void agesPastAMinuteAreInMinutes() {
        Freshness f = Freshness.of(List.of(ok("sonar", Duration.ofSeconds(150), Duration.ofMinutes(15))), NOW);
        assertEquals("frais il y a 2 min", f.text());
    }

    @Test
    void loopThatHasNotRunYetIsNotStale() {
        Freshness f = Freshness.of(List.of(
                ok("runners", Duration.ofSeconds(4), Duration.ofSeconds(15)),
                new LoopHealth("commits", null, null, null, Duration.ofHours(1))), NOW);
        assertFalse(f.stale());
        assertEquals("frais il y a 4 s", f.text());
    }

    @Test
    void nothingRunYetMeansCollecting() {
        Freshness f = Freshness.of(List.of(new LoopHealth("runners", null, null, null, Duration.ofSeconds(15))), NOW);
        assertEquals("collecte en cours…", f.text());
        assertFalse(f.stale());
    }

    @Test
    void lateLoopMakesTheBadgeStaleAndSaysWhy() {
        Freshness f = Freshness.of(List.of(
                ok("runners", Duration.ofSeconds(4), Duration.ofSeconds(15)),
                new LoopHealth("pulls", NOW.minus(Duration.ofMinutes(10)), NOW.minusSeconds(20),
                        "Vidocq: CodeFloe answered 502", Duration.ofSeconds(90))), NOW);
        assertTrue(f.stale());
        assertEquals("périmé", f.text());
        assertTrue(f.details().contains("pulls"), f.details());
        assertTrue(f.details().contains("CodeFloe answered 502"), f.details());
    }

    @Test
    void loopFailingSinceTheStartIsStale() {
        Freshness f = Freshness.of(List.of(
                new LoopHealth("runners", null, NOW.minusSeconds(5), "401", Duration.ofSeconds(15))), NOW);
        assertTrue(f.stale());
    }
}
