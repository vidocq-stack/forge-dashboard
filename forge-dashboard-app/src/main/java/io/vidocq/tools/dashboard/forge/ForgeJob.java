package io.vidocq.tools.dashboard.forge;

import java.time.Instant;
import java.util.List;

/**
 * A job of a workflow run.
 *
 * @param taskId    the runner task executing the job, 0 while it waits
 * @param startedAt the start of its first step, {@code null} when it has no step yet
 * @param stoppedAt the end of its last step, {@code null} until every step has ended
 */
public record ForgeJob(long id, long runId, long repoId, long taskId, String name, String status,
                       List<String> runsOn, String htmlUrl, Instant startedAt, Instant stoppedAt) {

    public ForgeJob {
        runsOn = runsOn == null ? List.of() : List.copyOf(runsOn);
    }
}
