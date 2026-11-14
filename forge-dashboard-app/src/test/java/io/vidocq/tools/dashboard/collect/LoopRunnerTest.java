package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.RateLimitedException;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.LoopHealth;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.testing.TestClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopRunnerTest {

    private static final Instant T0 = Instant.parse("2026-11-14T12:00:00Z");

    private final TestClock clock = new TestClock(T0);
    private final List<LoopHealth> published = new ArrayList<>();

    @Test
    void failingTickKeepsStateAndMarksStale() {
        LiveState live = new LiveState(new ChangeBus());
        live.update("Vidocq", s -> s.withRepos(List.of(), T0), new ChangeEvent.PullsChanged("Vidocq"));
        OrgSnapshot before = live.snapshot("Vidocq");
        LoopRunner runner = new LoopRunner("pulls", Duration.ofSeconds(90), () -> {
            throw new RateLimitedException(Duration.ofSeconds(600));
        }, clock, published::add);

        assertEquals(Duration.ofSeconds(600), runner.runOnce());

        assertNotNull(runner.health().lastError());
        assertEquals(T0, runner.health().lastFailure());
        assertTrue(runner.health().stale(T0.plus(Duration.ofHours(1))));
        assertSame(before, live.snapshot("Vidocq"));
        assertEquals(List.of(runner.health()), published);
    }

    @Test
    void successResetsFailures() {
        AtomicBoolean failing = new AtomicBoolean(true);
        LoopRunner runner = new LoopRunner("runners", Duration.ofSeconds(15), () -> {
            if (failing.get()) {
                throw new IllegalStateException("CodeFloe down");
            }
        }, clock, published::add);
        assertEquals(Duration.ofSeconds(30), runner.runOnce());
        assertEquals(Duration.ofSeconds(60), runner.runOnce());

        failing.set(false);
        clock.advance(Duration.ofMinutes(1));
        assertEquals(Duration.ofSeconds(15), runner.runOnce());
        assertEquals(T0.plus(Duration.ofMinutes(1)), runner.health().lastSuccess());
        assertFalse(runner.health().stale(clock.instant()));

        failing.set(true);
        assertEquals(Duration.ofSeconds(30), runner.runOnce());
        assertEquals(4, published.size());
    }

    @Test
    void oneFailingOrgDoesNotStopTheOthers() {
        List<String> done = new ArrayList<>();
        Runnable sweep = LoopRunner.eachOrg(() -> List.of("A", "B", "C"), org -> {
            if (org.equals("B")) {
                throw new IllegalStateException("B is down");
            }
            done.add(org);
        });

        var failure = assertThrows(IllegalStateException.class, sweep::run);

        assertEquals(List.of("A", "C"), done);
        assertTrue(failure.getMessage().contains("B"), failure.getMessage());
    }

    @Test
    void rateLimitStopsTheSweep() {
        List<String> done = new ArrayList<>();
        Runnable sweep = LoopRunner.eachOrg(() -> List.of("A", "B"), org -> {
            done.add(org);
            throw new RateLimitedException(Duration.ofSeconds(600));
        });

        var failure = assertThrows(RateLimitedException.class, sweep::run);

        assertEquals(List.of("A"), done);
        assertEquals(Duration.ofSeconds(600), failure.retryAfter());
    }
}
