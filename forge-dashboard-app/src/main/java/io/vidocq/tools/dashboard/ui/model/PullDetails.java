package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.forge.StatusContext;
import io.vidocq.tools.dashboard.sonar.GateStatus;

import java.util.List;

/** The PR drawer: its CI contexts, its Sonar gate and the jobs of its last run with their runners. */
public record PullDetails(String title, String author, CiState state, List<StatusContext> contexts,
                          GateStatus sonarGate, List<PullJobLine> lastRunJobs, String htmlUrl) {

    public PullDetails {
        contexts = List.copyOf(contexts);
        lastRunJobs = List.copyOf(lastRunJobs);
    }
}
