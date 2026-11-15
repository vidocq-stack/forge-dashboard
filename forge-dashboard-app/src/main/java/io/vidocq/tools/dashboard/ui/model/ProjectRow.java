package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.GateStatus;

/**
 * A line of the projects grid: the Sonar figures already formatted, « — » when Sonar has none; {@code error} when the
 * project is one the sort puts first (red main, red pull request other than a draft, failed gate).
 */
public record ProjectRow(String repo, CiState main, String loc, String tests, String coverage, GateStatus gate,
                         int prGreen, int prRed, int prPending, String lastCommit, String htmlUrl, boolean error) {


    public int pulls() {
        return prGreen + prRed + prPending;
    }
}
