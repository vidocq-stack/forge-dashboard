package io.vidocq.tools.dashboard.collect;

/** The outcome of adding an org: tracked, or refused with a reason to show to the admin. */
public sealed interface OrgAdminResult {

    record Added(String org) implements OrgAdminResult {
    }

    record Rejected(String reason) implements OrgAdminResult {
    }
}
