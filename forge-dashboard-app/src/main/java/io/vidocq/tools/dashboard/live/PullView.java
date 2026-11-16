package io.vidocq.tools.dashboard.live;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.forge.StatusContext;
import io.vidocq.tools.dashboard.sonar.GateStatus;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/** An open pull request, its combined CI state and, when Sonar is on, its quality gate. */
public record PullView(String repo, int number, String title, String author, String headSha, CiState state,
                       List<StatusContext> contexts, GateStatus sonarGate, String htmlUrl, Instant updatedAt) {

    /** "[WIP]", or the word WIP: not followed by a letter or a digit ("WIPE" is not a draft). */
    private static final Pattern WORK_IN_PROGRESS = Pattern.compile("\\s*(\\[WIP]|WIP(?![\\p{L}\\p{N}_]))",
            Pattern.CASE_INSENSITIVE);

    public PullView {
        contexts = List.copyOf(contexts);
    }

    /**
     * A work in progress: the title starts with "[WIP]" or with the word WIP, in any case, as Forgejo's drafts
     * ("WIP:") and as the ecosystem writes them ("WIP : …", "WIP docs: …"). Read from the title so that the pull
     * requests restored from the database have it too.
     */
    public boolean draft() {
        return title != null && WORK_IN_PROGRESS.matcher(title).lookingAt();
    }
}
