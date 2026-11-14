package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.ForgeJob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Compares the open executions recorded with the active jobs CodeFloe lists. Pure. */
public final class RunnerDiff {

    private static final String RUNNING = "running";

    private RunnerDiff() {
    }

    /**
     * @param knownRunningTaskIds the task ids of the executions recorded and not stopped
     * @param active              the organisation's active jobs
     */
    public static RunnerDiffResult diff(Set<Long> knownRunningTaskIds, List<ForgeJob> active) {
        List<ForgeJob> newRunning = new ArrayList<>();
        List<ForgeJob> waiting = new ArrayList<>();
        Set<Long> stillRunning = new HashSet<>();
        for (ForgeJob job : active) {
            if (RUNNING.equals(job.status())) {
                stillRunning.add(job.taskId());
                if (!knownRunningTaskIds.contains(job.taskId())) {
                    newRunning.add(job);
                }
            } else {
                waiting.add(job);
            }
        }
        Set<Long> finished = new HashSet<>(knownRunningTaskIds);
        finished.removeAll(stillRunning);
        return new RunnerDiffResult(newRunning, finished, waiting);
    }
}
