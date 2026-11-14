package io.vidocq.tools.dashboard.forge;

/** A repository of an organisation. */
public record ForgeRepo(long id, String owner, String name, String defaultBranch, boolean archived, boolean empty) {
}
