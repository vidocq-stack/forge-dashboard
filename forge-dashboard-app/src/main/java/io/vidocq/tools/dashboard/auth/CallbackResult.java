package io.vidocq.tools.dashboard.auth;

/** What the callback does with the browser. */
public sealed interface CallbackResult {

    /** Logged in: the session gets the user, the browser goes back where it started. */
    record Success(DashboardUser user, String returnTo) implements CallbackResult {
    }

    /** A clear 403 page. */
    record Denied(String reason) implements CallbackResult {
    }

    /** No login of this session matches: one redirect to {@code /}, which starts a new one. */
    record Restart() implements CallbackResult {
    }
}
