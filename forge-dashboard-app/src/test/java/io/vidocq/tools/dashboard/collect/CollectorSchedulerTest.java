package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.RateLimitedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectorSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);
    private static final Duration HOUR = Duration.ofHours(1);
    /** The commits loop's shortcut while a back-fill is pending. */
    private static final UnaryOperator<Duration> BACKFILL = next -> Duration.ofSeconds(60);

    /** During an outage or a rate limit the back-fill must not turn the backoff into a retry every minute. */
    @Test
    void backfillShortcutWaitsForASuccessfulPass() {
        var limited = new LoopRunner("commits", HOUR,
                () -> { throw new RateLimitedException(Duration.ofMinutes(10)); }, CLOCK, h -> { });
        Duration backoff = limited.runOnce();

        assertEquals(backoff, CollectorScheduler.nextDelay(limited, backoff, BACKFILL));
    }

    @Test
    void backfillShortcutAppliesAfterASuccessfulPass() {
        var ok = new LoopRunner("commits", HOUR, () -> { }, CLOCK, h -> { });

        assertEquals(Duration.ofSeconds(60), CollectorScheduler.nextDelay(ok, ok.runOnce(), BACKFILL));
    }
}
