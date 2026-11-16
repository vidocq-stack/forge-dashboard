package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.GateStatus;

/**
 * A line of the projects grid: the Sonar figures already formatted, « — » when Sonar has none; {@code error} when the
 * project is one the sort puts first (red main, red pull request other than a draft, failed gate). The PRs ready for
 * review count by state (green, red, running); the drafts, whatever their state, count apart ({@code prWip}).
 */
public record ProjectRow(String repo, CiState main, String loc, String tests, String coverage, GateStatus gate,
                         int prGreen, int prRed, int prPending, int prWip, String lastCommit, String htmlUrl,
                         boolean error) {

    public int pulls() {
        return prGreen + prRed + prPending + prWip;
    }
}
