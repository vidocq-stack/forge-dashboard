package io.vidocq.tools.dashboard.domain;

import java.time.Instant;

/**
 * A job held by a runner from {@code start} to {@code stop}, or still running when {@code stop} is null. As stored:
 * the caller clips it to its window.
 */
public record Interval(long runnerId, Instant start, Instant stop) {
}
