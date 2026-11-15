package io.vidocq.tools.dashboard.domain;

/** A job of an org's repository, by its name in the workflow: what its usual duration is kept for. */
public record JobKey(String repo, String jobName) {
}
