package io.vidocq.tools.dashboard.ui.model;

/**
 * A job of the last run of a PR: the runner that took it ("?" while unknown), its status, its duration so far and,
 * running, how far along it is.
 */
public record PullJobLine(String jobName, Long runnerId, String runnerName, String status, String duration,
                          String htmlUrl) {
}
