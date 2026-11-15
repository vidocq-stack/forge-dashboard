package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.GateStatus;

import java.util.Map;

/**
 * A line of the projects grid: the Sonar figures already formatted, « — » when Sonar has none; {@code error} when the
 * project is one the sort puts first (red main, red pull request other than a draft, failed gate). How far along the
 * running builds are (see {@link Progress#ofBuild}): {@code mainProgress}, null unless main runs with an estimate, and
 * {@code pullProgress} by PR number, for the PRs that do.
 */
public record ProjectRow(String repo, CiState main, String loc, String tests, String coverage, GateStatus gate,
                         int prGreen, int prRed, int prPending, String lastCommit, String htmlUrl, boolean error,
                         String mainProgress, Map<Integer, String> pullProgress) {

    public ProjectRow {
        pullProgress = Map.copyOf(pullProgress);
    }

    public int pulls() {
        return prGreen + prRed + prPending;
    }
}
