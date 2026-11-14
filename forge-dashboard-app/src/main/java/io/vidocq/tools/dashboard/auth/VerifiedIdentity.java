package io.vidocq.tools.dashboard.auth;

import java.util.Set;

/** The claims of a valid id_token that the dashboard uses. */
public record VerifiedIdentity(String login, String name, String picture, Set<String> groups, String nonce) {

    public VerifiedIdentity {
        groups = groups == null ? Set.of() : Set.copyOf(groups);
    }
}
