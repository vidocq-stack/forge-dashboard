package io.vidocq.tools.dashboard.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OccupancyCalculatorTest {

    private static Instant t(long s) {
        return Instant.EPOCH.plusSeconds(s);
    }

    @Test
    void slotAndAnyBusyRatios() {
        var jobs = List.of(new Interval(1, t(10), t(60)), new Interval(1, t(20), t(40)));
        var o = OccupancyCalculator.compute(jobs, List.of(), 2, t(0), t(100));
        assertEquals(0.35, o.slotRatio(), 1e-9);
        assertEquals(0.50, o.anyBusyRatio(), 1e-9);
    }

    @Test
    void offlineTimeIsExcluded() {
        var o = OccupancyCalculator.compute(List.of(new Interval(1, t(0), t(40))),
                List.of(new StatusSpan(1, "offline", t(80)), new StatusSpan(1, "idle", t(100))), 1, t(0), t(100));
        assertEquals(0.5, o.slotRatio(), 1e-9);   // 40 / 80
        assertEquals(0.5, o.anyBusyRatio(), 1e-9);
    }

    @Test
    void offlineBeforeTheWindowCountsFromItsStart() {
        // offline since before the window until t(50): only [0, 50) is removed.
        var o = OccupancyCalculator.compute(List.of(new Interval(1, t(60), t(85))),
                List.of(new StatusSpan(1, "offline", t(-30)), new StatusSpan(1, "idle", t(50))), 1, t(0), t(100));
        assertEquals(0.5, o.slotRatio(), 1e-9);   // 25 / 50
    }

    @Test
    void openIntervalRunsToWindowEnd() {
        var o = OccupancyCalculator.compute(List.of(new Interval(1, t(50), null)), List.of(), 1, t(0), t(100));
        assertEquals(0.5, o.slotRatio(), 1e-9);
        assertEquals(0.5, o.anyBusyRatio(), 1e-9);
    }

    @Test
    void intervalsAreClippedToTheWindow() {
        var o = OccupancyCalculator.compute(List.of(new Interval(1, t(-50), t(10)), new Interval(1, t(90), t(200))),
                List.of(), 1, t(0), t(100));
        assertEquals(0.2, o.slotRatio(), 1e-9);
    }

    @Test
    void ratioNeverExceedsOne() {
        var jobs = List.of(new Interval(1, t(0), t(100)), new Interval(1, t(0), t(100)));
        assertEquals(1.0, OccupancyCalculator.compute(jobs, List.of(), 1, t(0), t(100)).slotRatio(), 1e-9);
    }

    @Test
    void alwaysOfflineGivesZero() {
        var o = OccupancyCalculator.compute(List.of(), List.of(new StatusSpan(1, "offline", t(-10))), 1, t(0), t(100));
        assertEquals(0.0, o.slotRatio(), 1e-9);
        assertEquals(0.0, o.anyBusyRatio(), 1e-9);
    }

    @Test
    void observedCapacityIsMaxOverlap() {
        assertEquals(2, OccupancyCalculator.observedCapacity(
                List.of(new Interval(1, t(10), t(60)), new Interval(1, t(20), t(40)))));
        assertEquals(1, OccupancyCalculator.observedCapacity(List.of()));
        // [a, b) then [b, c): back to back, never together.
        assertEquals(1, OccupancyCalculator.observedCapacity(
                List.of(new Interval(1, t(0), t(10)), new Interval(1, t(10), t(20)))));
        assertEquals(3, OccupancyCalculator.observedCapacity(List.of(new Interval(1, t(0), null),
                new Interval(1, t(5), t(30)), new Interval(1, t(20), null))));
    }
}
