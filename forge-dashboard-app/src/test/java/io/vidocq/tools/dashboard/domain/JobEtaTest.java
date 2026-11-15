package io.vidocq.tools.dashboard.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobEtaTest {

    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    @Test
    void threeMinutesOfFiveIsSixtyPercentWithTwoLeft() {
        JobEta eta = JobEta.of(Duration.ofMinutes(3), FIVE_MINUTES).orElseThrow();
        assertEquals(60, eta.percent());
        assertEquals(Duration.ofMinutes(2), eta.left());
        assertFalse(eta.overrun());
    }

    /** Past its usual duration a job never shows 100 %: it is not done, it runs late. */
    @Test
    void pastItsUsualDurationItStaysAtNinetyNineAndRunsLate() {
        JobEta eta = JobEta.of(Duration.ofMinutes(6), FIVE_MINUTES).orElseThrow();
        assertEquals(99, eta.percent());
        assertEquals(Duration.ofMinutes(-1), eta.left());
        assertTrue(eta.overrun());
    }

    @Test
    void withoutAUsualDurationThereIsNoEstimate() {
        assertEquals(Optional.empty(), JobEta.of(Duration.ofMinutes(3), null));
        assertEquals(Optional.empty(), JobEta.of(Duration.ofMinutes(3), Duration.ZERO));
        assertEquals(Optional.empty(), JobEta.of(null, FIVE_MINUTES));
    }

    /** A start a little ahead of our clock (Forgejo's clock, rounding) counts as just started. */
    @Test
    void aStartAheadOfTheClockCountsAsJustStarted() {
        JobEta eta = JobEta.of(Duration.ofSeconds(-5), FIVE_MINUTES).orElseThrow();
        assertEquals(0, eta.percent());
        assertEquals(FIVE_MINUTES, eta.left());
    }

    /** A build waits for its slowest running job: the one with the most time left, or the latest of the late ones. */
    @Test
    void aBuildStandsWhereItsLongestJobStands() {
        JobEta twoLeft = new JobEta(60, Duration.ofMinutes(2));
        JobEta eightLeft = new JobEta(20, Duration.ofMinutes(8));
        JobEta late = new JobEta(99, Duration.ofMinutes(-1));
        assertEquals(Optional.of(eightLeft), JobEta.longest(List.of(twoLeft, eightLeft, late)));
        assertEquals(Optional.of(twoLeft), JobEta.longest(List.of(late, twoLeft)));
        assertEquals(Optional.empty(), JobEta.longest(List.of()));
    }
}
