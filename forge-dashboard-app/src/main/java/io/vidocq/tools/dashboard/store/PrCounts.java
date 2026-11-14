package io.vidocq.tools.dashboard.store;

/** The pull requests a login opened, and those of its pull requests merged, over a period. */
public record PrCounts(String login, long opened, long merged) {
}
