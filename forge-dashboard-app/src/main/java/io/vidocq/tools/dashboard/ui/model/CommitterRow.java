package io.vidocq.tools.dashboard.ui.model;

import java.util.LinkedHashMap;

/**
 * A committer over the period: commits, lines, PRs, and the commits per part — {@code "seul"} first (no agent
 * trailer), then the agent families by volume. A commit with several agents counts in each family, so the parts may
 * add up to more than {@code commits}.
 */
public record CommitterRow(String humanKey, long commits, long additions, long deletions, long prsOpened,
                           long prsMerged, LinkedHashMap<String, Long> breakdown) {

    public static final String ALONE = "seul";

    public CommitterRow {
        breakdown = new LinkedHashMap<>(breakdown);
    }

    /** The sum of the parts: above {@code commits} when commits had several agents. */
    public long breakdownTotal() {
        return breakdown.values().stream().mapToLong(Long::longValue).sum();
    }
}
