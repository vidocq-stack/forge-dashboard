package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.i18n.Messages;

import java.util.Set;

/** The status of a Forgejo Actions job or run in the user's language; a status Forgejo adds later shows as it comes. */
public final class StatusLabel {

    private static final Set<String> KNOWN =
            Set.of("success", "failure", "cancelled", "skipped", "running", "waiting", "blocked", "unknown");

    private StatusLabel() {
    }

    public static String of(String status, Messages m) {
        if (status == null) {
            return "";
        }
        return KNOWN.contains(status) ? m.text("ci.status." + status) : status;
    }
}
