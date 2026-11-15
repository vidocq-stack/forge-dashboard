package io.vidocq.tools.dashboard.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsualDurationsTest {

    private static List<Duration> seconds(long... values) {
        return java.util.Arrays.stream(values).mapToObj(Duration::ofSeconds).toList();
    }

    /** One or two runs say too little: a cold cache or a flaky step would set the estimate. */
    @Test
    void fewerThanThreeRunsGiveNoUsualDuration() {
        assertEquals(Optional.empty(), UsualDurations.median(seconds()));
        assertEquals(Optional.empty(), UsualDurations.median(seconds(60, 120)));
    }

    /** The median, not the mean: one very slow run does not drag the estimate. */
    @Test
    void theMedianOfTheRuns() {
        assertEquals(Optional.of(Duration.ofSeconds(120)), UsualDurations.median(seconds(600, 60, 120)));
        assertEquals(Optional.of(Duration.ofSeconds(150)), UsualDurations.median(seconds(180, 60, 3_000, 120)));
    }
}
