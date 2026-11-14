package io.vidocq.tools.dashboard.domain;

/**
 * The occupancy of a runner over a window, offline time excluded: {@code slotRatio} is the share of its slots in use,
 * {@code anyBusyRatio} the share of the time it ran at least one job. Both between 0 and 1.
 */
public record Occupancy(double slotRatio, double anyBusyRatio) {
}
