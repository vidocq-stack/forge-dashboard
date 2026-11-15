package io.vidocq.tools.dashboard.auth;

import java.util.List;

/** What the callback does with the browser. */
public sealed interface CallbackResult {

    /** Logged in: the session gets the user, the browser goes back where it started. */
    record Success(DashboardUser user, String returnTo) implements CallbackResult {
    }

    /** A clear 403 page; the reason is a text key and its arguments, shown in the browser's language. */
    record Denied(String key, List<String> args) implements CallbackResult {

        public Denied {
            args = List.copyOf(args);
        }
    }

    /** No login of this session matches: one redirect to {@code /}, which starts a new one. */
    record Restart() implements CallbackResult {
    }
}
