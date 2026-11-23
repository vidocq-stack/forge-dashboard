package io.vidocq.tools.dashboard.live;

/**
 * A component of a pull request, by its tag (« VAUBAN »): read in the title, deduced from the changed files (the
 * monorepo's {@code components.json}), or both.
 */
public record ComponentTag(String tag, boolean inTitle, boolean inFiles) {
}
