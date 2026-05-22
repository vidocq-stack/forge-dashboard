package io.vidocq.tools.dashboard.model;

import java.time.Instant;

public record BuildStatus(
        State state,
        String workflow,
        String runId,
        Instant startedAt,
        Instant finishedAt,
        String htmlUrl) {

    public enum State {
        SUCCESS, FAILURE, RUNNING, CANCELLED, UNKNOWN
    }
}
