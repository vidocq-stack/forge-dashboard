package io.vidocq.tools.dashboard.domain;

import java.time.Instant;

/**
 * A commit as read from the forge, before classification.
 *
 * @param authorLogin the CodeFloe login of the author, or {@code null} when the forge could not link the mail
 */
public record RawCommit(String sha, String org, String repo, Instant authoredAt, String authorName,
                        String authorEmail, String authorLogin, String message, int additions, int deletions) {
}
