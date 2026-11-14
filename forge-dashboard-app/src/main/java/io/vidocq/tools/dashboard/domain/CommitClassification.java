package io.vidocq.tools.dashboard.domain;

import java.util.List;

/**
 * Who a commit is attributed to.
 *
 * @param humanKey      the human committer (login, alias or mail), {@code null} for a bot author
 * @param agentFamilies the distinct agent families of the {@code Co-Authored-By} trailers, in order of appearance
 * @param claudeSession the {@code Claude-Session} trailer, or {@code null}
 * @param rawTrailers   every {@code Co-Authored-By} and {@code Claude-Session} line, as written
 */
public record CommitClassification(CommitKind kind, String humanKey, List<String> agentFamilies,
                                   String claudeSession, List<String> rawTrailers) {

    public CommitClassification {
        agentFamilies = List.copyOf(agentFamilies);
        rawTrailers = List.copyOf(rawTrailers);
    }
}
