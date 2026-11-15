package io.vidocq.tools.dashboard.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The return from CodeFloe: checks it belongs to the login this session started, exchanges the code, checks the
 * id_token and who may come in. Without a matching login it restarts one; it never redirects off the dashboard.
 */
public final class CallbackHandler {

    private static final System.Logger LOG = System.getLogger(CallbackHandler.class.getName());
    private static final int MAX_ERROR_LENGTH = 80;

    private final OidcSettings settings;
    private final TokenEndpoint tokens;
    private final IdTokenVerifier verifier;

    public CallbackHandler(OidcSettings s, TokenEndpoint tokens, IdTokenVerifier verifier) {
        this.settings = s;
        this.tokens = tokens;
        this.verifier = verifier;
    }

    public CallbackResult handle(Map<String, String> query, LoginState saved) {
        if (saved == null) {
            return new CallbackResult.Restart();
        }
        String error = query.get("error");
        if (error != null) {
            return new CallbackResult.Denied("auth.denied.byCodefloe", List.of(abbreviate(error)));
        }
        if (!same(query.get("state"), saved.state())) {
            return new CallbackResult.Restart();
        }
        String code = query.get("code");
        if (code == null || code.isBlank()) {
            return new CallbackResult.Restart();
        }
        TokenResponse response;
        try {
            response = tokens.exchange(code, saved.verifier(), settings.redirectUri());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "CodeFloe code exchange failed: " + e.getMessage());
            return new CallbackResult.Denied("auth.denied.exchangeFailed", List.of());
        }
        VerifiedIdentity id;
        try {
            id = verifier.verify(response.idToken());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "CodeFloe id_token rejected: " + e.getMessage());
            return new CallbackResult.Denied("auth.denied.invalidToken", List.of());
        }
        if (!same(id.nonce(), saved.nonce())) {
            return new CallbackResult.Denied("auth.denied.tokenMismatch", List.of());
        }
        if (!GroupGuard.allowed(id.groups(), settings.allowedGroups())) {
            LOG.log(System.Logger.Level.INFO, "Login refused for " + id.login() + ": not in " + settings.allowedGroups());
            return new CallbackResult.Denied("auth.denied.groups",
                    List.of(String.join(", ", new TreeSet<>(settings.allowedGroups()))));
        }
        boolean admin = !Collections.disjoint(id.groups(), settings.adminGroups());
        String name = id.name() == null || id.name().isBlank() ? id.login() : id.name();
        var user = new DashboardUser(id.login(), name, id.picture(), id.groups(), admin);
        return new CallbackResult.Success(user, safeReturnTo(saved.returnTo()));
    }

    /** A path of this site only: {@code /…}, never {@code //host} or {@code /\host}, nor a control character. */
    static String safeReturnTo(String target) {
        if (target == null || !target.startsWith("/") || target.startsWith("//") || target.indexOf('\\') >= 0
                || target.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            return "/";
        }
        return target;
    }

    private static boolean same(String received, String expected) {
        return received != null && expected != null && MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static String abbreviate(String s) {
        return s.length() > MAX_ERROR_LENGTH ? s.substring(0, MAX_ERROR_LENGTH) + "…" : s;
    }
}
