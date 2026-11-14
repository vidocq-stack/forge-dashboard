package io.vidocq.tools.dashboard.live;

import java.time.Instant;

/** A job waiting or running. {@code runnerId} and {@code runnerName} are null while waiting or unresolved. */
public record JobView(long taskId, long jobId, String repo, Integer prNumber, String ref, String jobName, String status,
                      Instant startedAt, String htmlUrl, Long runnerId, String runnerName) {
}
