package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.GateStatus;

/** A line of the projects grid: the Sonar figures already formatted, « — » when Sonar has none. */
public record ProjectRow(String repo, CiState main, String loc, String tests, String coverage, GateStatus gate,
                         int prGreen, int prRed, int prPending, String lastCommit, String htmlUrl) {

    /** The PR column: {@code 1✅ 0❌ 1⏳}. */
    public String pullCounts() {
        return prGreen + "✅ " + prRed + "❌ " + prPending + "⏳";
    }

    public int pulls() {
        return prGreen + prRed + prPending;
    }
}
