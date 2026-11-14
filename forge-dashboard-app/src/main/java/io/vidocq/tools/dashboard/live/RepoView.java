package io.vidocq.tools.dashboard.live;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;

import java.time.Instant;
import java.util.List;

/** A repository: the state of its default branch, its last commit, its Sonar measures (null without) and open PRs. */
public record RepoView(String name, CiState mainState, String mainSha, String lastCommitTitle, Instant lastCommitAt,
                       SonarMeasures sonar, List<PullView> pulls, String htmlUrl) {

    public RepoView {
        pulls = List.copyOf(pulls);
    }
}
