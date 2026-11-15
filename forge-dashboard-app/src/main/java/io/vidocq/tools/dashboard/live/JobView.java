package io.vidocq.tools.dashboard.live;

import java.time.Duration;
import java.time.Instant;

/**
 * A job waiting or running. {@code runnerId} and {@code runnerName} are null while waiting or unresolved;
 * {@code usual}, the job's usual duration, is null without enough history (see
 * {@link io.vidocq.tools.dashboard.domain.UsualDurations}).
 */
public record JobView(long taskId, long jobId, String repo, Integer prNumber, String ref, String jobName, String status,
                      Instant startedAt, String htmlUrl, Long runnerId, String runnerName, Duration usual) {
}
