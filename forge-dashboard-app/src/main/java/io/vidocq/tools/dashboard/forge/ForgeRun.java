package io.vidocq.tools.dashboard.forge;

import java.time.Instant;

/**
 * A workflow run.
 *
 * @param prettyRef {@code #298} for a pull request run, else a branch name or a commit
 */
public record ForgeRun(long id, String event, String prettyRef, String commitSha, String title, String status,
                       Instant started, Instant stopped, String htmlUrl) {
}
