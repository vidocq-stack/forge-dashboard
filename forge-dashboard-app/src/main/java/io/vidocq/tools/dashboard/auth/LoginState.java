package io.vidocq.tools.dashboard.auth;

import java.io.Serializable;

/** What a login sent to CodeFloe must find again at the callback, kept in the HTTP session. */
public record LoginState(String state, String nonce, String verifier, String returnTo) implements Serializable {

    public static final String ATTR = "dashboard.login";
}
