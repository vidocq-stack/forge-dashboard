package io.vidocq.tools.dashboard.auth;

import java.util.Collections;
import java.util.Set;

/** Whether a CodeFloe user belongs to one of the groups let in ({@code org} or {@code org:team}). */
public final class GroupGuard {

    private GroupGuard() {
    }

    public static boolean allowed(Set<String> userGroups, Set<String> allowed) {
        return !Collections.disjoint(userGroups, allowed);
    }
}
