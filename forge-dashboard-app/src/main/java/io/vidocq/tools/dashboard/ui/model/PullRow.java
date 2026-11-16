package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.live.PullView;

/** A line of the PR tab: an open PR of the org and when it last changed, already worded (« il y a 3 h »). */
public record PullRow(PullView pull, String updated) {

    /** A red PR ready for review: the tab shows it first, in red. */
    public boolean error() {
        return pull.state() == CiState.FAILURE && !pull.draft();
    }
}
