package io.vidocq.tools.dashboard.live;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.forge.StatusContext;
import io.vidocq.tools.dashboard.sonar.GateStatus;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** An open pull request, its combined CI state and, when Sonar is on, its quality gate. */
public record PullView(String repo, int number, String title, String author, String headSha, CiState state,
                       List<StatusContext> contexts, GateStatus sonarGate, String htmlUrl, Instant updatedAt) {

    public PullView {
        contexts = List.copyOf(contexts);
    }

    /**
     * A work in progress, as Forgejo marks its drafts: the title starts with one of its default prefixes, "WIP:" or
     * "[WIP]", in any case. Read from the title so that the pull requests restored from the database have it too.
     */
    public boolean draft() {
        if (title == null) {
            return false;
        }
        String upper = title.toUpperCase(Locale.ROOT);
        return upper.startsWith("WIP:") || upper.startsWith("[WIP]");
    }
}
