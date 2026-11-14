package io.vidocq.tools.dashboard.domain;

import java.time.Instant;

/** A runner entering {@code status} (idle, active, offline) at {@code at}, until its next change. */
public record StatusSpan(long runnerId, String status, Instant at) {
}
