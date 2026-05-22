package io.vidocq.tools.dashboard.model;

import java.time.Instant;
import java.util.List;

public record RunnerInfo(
        long id,
        String name,
        String version,
        Status status,
        List<String> labels,
        Instant lastOnline,
        String htmlUrl) {

    public enum Status {
        ONLINE, IDLE, BUSY, OFFLINE, UNKNOWN
    }
}
