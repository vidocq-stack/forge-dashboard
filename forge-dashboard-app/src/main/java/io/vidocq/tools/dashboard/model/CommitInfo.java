package io.vidocq.tools.dashboard.model;

import java.time.Instant;

public record CommitInfo(
        String sha,
        String shortSha,
        String message,
        String author,
        Instant timestamp,
        String htmlUrl) {
}
