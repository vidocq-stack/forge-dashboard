package io.vidocq.tools.dashboard.live;

import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The loops take their time from an injected Clock, so that the tests can pass a fixed one. */
class ClockProducerIntegrationTest {

    @Test
    void theApplicationClockIsUtc() {
        Clock clock = InProcessApp.bean(Clock.class);
        assertEquals(ZoneOffset.UTC, clock.getZone());
        assertTrue(Duration.between(clock.instant(), Instant.now()).abs().toSeconds() < 5);
    }
}
