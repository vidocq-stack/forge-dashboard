package io.vidocq.tools.dashboard.collect;

import java.util.List;

/** The outcome of adding an org: tracked, or refused with a reason to show to the admin (a text key and its arguments). */
public sealed interface OrgAdminResult {

    record Added(String org) implements OrgAdminResult {
    }

    record Rejected(String key, List<String> args) implements OrgAdminResult {

        public Rejected {
            args = List.copyOf(args);
        }
    }
}
