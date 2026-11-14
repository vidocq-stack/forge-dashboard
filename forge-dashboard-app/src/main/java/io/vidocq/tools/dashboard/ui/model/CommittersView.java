package io.vidocq.tools.dashboard.ui.model;

import java.util.List;

/** People (with the agents they worked with), bot accounts, and the agent commits no PR could attribute. */
public record CommittersView(List<CommitterRow> humans, List<CommitterRow> automation, CommitterRow unattributed) {

    public CommittersView {
        humans = List.copyOf(humans);
        automation = List.copyOf(automation);
    }
}
