package io.vidocq.tools.dashboard.forge;

import java.time.Instant;

/** A pull request. {@code mergedAt} is {@code null} until it is merged. */
public record ForgePull(int number, String title, String authorLogin, String headRef, String headSha,
                        String htmlUrl, Instant createdAt, Instant updatedAt, Instant mergedAt, boolean draft) {
}
