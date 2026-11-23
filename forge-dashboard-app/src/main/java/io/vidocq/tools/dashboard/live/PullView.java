package io.vidocq.tools.dashboard.live;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.forge.StatusContext;
import io.vidocq.tools.dashboard.sonar.GateStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An open pull request, its combined CI state and, when Sonar is on, its quality gate. {@code fileTags}: the tags of
 * the components its changed files touch, in a repository with a {@code components.json}; empty elsewhere.
 */
public record PullView(String repo, int number, String title, String author, String headSha, CiState state,
                       List<StatusContext> contexts, GateStatus sonarGate, String htmlUrl, Instant updatedAt,
                       List<String> fileTags) {

    /** "[WIP]", or the word WIP: not followed by a letter or a digit ("WIPE" is not a draft). */
    private static final Pattern WORK_IN_PROGRESS = Pattern.compile("\\s*(\\[WIP]|WIP(?![\\p{L}\\p{N}_]))",
            Pattern.CASE_INSENSITIVE);

    /** A component tag of a title, brackets included, spaces allowed before it: "[NOETHER]". */
    private static final Pattern TAG = Pattern.compile("\\s*(\\[[A-Z][A-Z0-9_-]*])");

    /** The ":" and the spaces that can follow a WIP marker or a tag in a title. */
    private static final Pattern MARKER_TAIL = Pattern.compile("^[\\s:]+");

    public PullView {
        contexts = List.copyOf(contexts);
        fileTags = fileTags == null ? List.of() : List.copyOf(fileTags);
    }

    /** A pull request whose changed files are not known (yet). */
    public PullView(String repo, int number, String title, String author, String headSha, CiState state,
                    List<StatusContext> contexts, GateStatus sonarGate, String htmlUrl, Instant updatedAt) {
        this(repo, number, title, author, headSha, state, contexts, sonarGate, htmlUrl, updatedAt, List.of());
    }

    public PullView withFileTags(List<String> tags) {
        return new PullView(repo, number, title, author, headSha, state, contexts, sonarGate, htmlUrl, updatedAt,
                tags);
    }

    public PullView withSonarGate(GateStatus gate) {
        return new PullView(repo, number, title, author, headSha, state, contexts, gate, htmlUrl, updatedAt,
                fileTags);
    }

    /**
     * A work in progress: the title starts with "[WIP]" or with the word WIP, in any case, as Forgejo's drafts
     * ("WIP:") and as the ecosystem writes them ("WIP : …", "WIP docs: …"). Read from the title so that the pull
     * requests restored from the database have it too.
     */
    public boolean draft() {
        return title != null && WORK_IN_PROGRESS.matcher(title).lookingAt();
    }

    /**
     * The component tags at the start of the title, after a WIP marker if any ("[WIP]" is the draft marker, not a
     * tag): "[WIP] [NOETHER] fix: …" gives ["NOETHER"]. Empty without a title or a leading tag, so that a tag
     * elsewhere in the title ("fix: handle [FOY] header") is not a component.
     */
    public List<String> tags() {
        return head().tags();
    }

    /**
     * The component tags of the pull request: those of its title, in the title's order, then those of its changed
     * files that the title does not name, in the order of {@code components.json}.
     */
    public List<ComponentTag> componentTags() {
        List<String> titled = tags();
        List<ComponentTag> out = new ArrayList<>();
        for (String tag : titled) {
            if (out.stream().noneMatch(c -> c.tag().equals(tag))) {
                out.add(new ComponentTag(tag, true, fileTags.contains(tag)));
            }
        }
        for (String tag : fileTags) {
            if (!titled.contains(tag)) {
                out.add(new ComponentTag(tag, false, true));
            }
        }
        return out;
    }

    /**
     * The components the pull request touches: its component tags in lower case or, without any, its repository —
     * "vauban" is the vauban repository and the [VAUBAN] pull requests of the monorepo.
     */
    public List<String> components() {
        List<ComponentTag> tags = componentTags();
        if (!tags.isEmpty()) {
            return tags.stream().map(c -> c.tag().toLowerCase(Locale.ROOT)).toList();
        }
        return List.of(repo);
    }

    /**
     * The title without its WIP marker and its leading tags, nor the ":" and the spaces that follow them; the whole
     * title when nothing would be left ("[WIP]", "[RUNTIME]"), empty without a title.
     */
    public String shortTitle() {
        String rest = head().rest();
        if (rest.isEmpty()) {
            return title == null ? "" : title;
        }
        return rest;
    }

    /** The leading tags of the title and the title after the WIP marker and those tags. */
    private Head head() {
        if (title == null) {
            return new Head(List.of(), "");
        }
        Matcher wip = WORK_IN_PROGRESS.matcher(title);
        boolean marked = wip.lookingAt();
        // « WIP: [NOETHER] … », Forgejo's own draft prefix: its ":" comes before the tags
        String rest = marked ? MARKER_TAIL.matcher(title.substring(wip.end())).replaceFirst("") : title;
        List<String> tags = new ArrayList<>();
        Matcher tag = TAG.matcher(rest);
        while (tag.lookingAt() && !tag.group(1).equals("[WIP]")) {
            tags.add(tag.group(1).substring(1, tag.group(1).length() - 1));
            rest = rest.substring(tag.end());
            tag = TAG.matcher(rest);
        }
        if (!tags.isEmpty()) {
            rest = MARKER_TAIL.matcher(rest).replaceFirst("");
        }
        return new Head(List.copyOf(tags), rest);
    }

    private record Head(List<String> tags, String rest) {
    }
}
