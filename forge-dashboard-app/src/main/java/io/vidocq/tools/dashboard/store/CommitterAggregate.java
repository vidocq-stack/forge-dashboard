package io.vidocq.tools.dashboard.store;

import io.vidocq.tools.dashboard.domain.CommitKind;

/**
 * The commits of one human (null for the bots) with one set of agents ({@code agentFamilies}, comma-separated, null
 * without agent), and their line counts.
 */
public record CommitterAggregate(String humanKey, CommitKind kind, String agentFamilies, long commits, long additions,
                                 long deletions) {
}
