package io.vidocq.tools.dashboard.domain;

/** The kind of author of a commit. */
public enum CommitKind {
    /** A person, possibly helped by agents named in the trailers. */
    HUMAN,
    /** A bot account, with no agent trailer. */
    BOT,
    /** A bot account committing an agent's work; the human is found through the pull request. */
    AGENT_VIA_BOT
}
