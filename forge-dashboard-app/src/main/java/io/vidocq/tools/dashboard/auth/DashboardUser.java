package io.vidocq.tools.dashboard.auth;

import java.io.Serializable;
import java.util.Set;

/** The CodeFloe user of a session; {@code admin} may add and remove organisations. */
public record DashboardUser(String login, String name, String pictureUrl, Set<String> groups, boolean admin)
        implements Serializable {

    public static final String ATTR = "dashboard.user";

    public DashboardUser {
        groups = Set.copyOf(groups);
    }
}
