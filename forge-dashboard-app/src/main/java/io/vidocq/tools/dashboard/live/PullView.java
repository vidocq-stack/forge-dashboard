package io.vidocq.tools.dashboard.live;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.forge.StatusContext;
import io.vidocq.tools.dashboard.sonar.GateStatus;

import java.time.Instant;
import java.util.List;

/** An open pull request, its combined CI state and, when Sonar is on, its quality gate. */
public record PullView(String repo, int number, String title, String author, String headSha, CiState state,
                       List<StatusContext> contexts, GateStatus sonarGate, String htmlUrl, Instant updatedAt) {

    public PullView {
        contexts = List.copyOf(contexts);
    }
}
