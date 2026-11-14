package io.vidocq.tools.dashboard.collect;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackoffTest {

    @Test
    void backoffDoublesAndCaps() {
        assertEquals(Duration.ofSeconds(15), Backoff.next(Duration.ofSeconds(15), 0, Optional.empty()));
        assertEquals(Duration.ofSeconds(30), Backoff.next(Duration.ofSeconds(15), 1, Optional.empty()));
        assertEquals(Duration.ofSeconds(120), Backoff.next(Duration.ofSeconds(15), 3, Optional.empty()));
        assertEquals(Duration.ofMinutes(15), Backoff.next(Duration.ofSeconds(15), 10, Optional.empty()));
        assertEquals(Duration.ofSeconds(600),
                Backoff.next(Duration.ofSeconds(15), 1, Optional.of(Duration.ofSeconds(600))));
    }

    @Test
    void backoffNeverRetriesSoonerThanTheCadence() {
        assertEquals(Duration.ofHours(1), Backoff.next(Duration.ofHours(1), 1, Optional.empty()));
        assertEquals(Duration.ofHours(1), Backoff.next(Duration.ofHours(1), 40, Optional.empty()));
    }

    @Test
    void backoffSurvivesManyFailures() {
        assertEquals(Duration.ofMinutes(15), Backoff.next(Duration.ofSeconds(15), 1_000, Optional.empty()));
    }
}
