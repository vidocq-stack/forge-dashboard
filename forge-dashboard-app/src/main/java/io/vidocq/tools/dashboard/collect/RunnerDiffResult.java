package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.ForgeJob;

import java.util.List;
import java.util.Set;

/** The running jobs not recorded yet, the recorded tasks no longer active, and the waiting jobs. */
public record RunnerDiffResult(List<ForgeJob> newRunning, Set<Long> finishedTaskIds, List<ForgeJob> waiting) {

    public RunnerDiffResult {
        newRunning = List.copyOf(newRunning);
        finishedTaskIds = Set.copyOf(finishedTaskIds);
        waiting = List.copyOf(waiting);
    }
}
