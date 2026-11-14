package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.store.JobExecution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The recent jobs of a runner, grouped by PR, or by ref for the pushes; the groups in the order of their latest job,
 * at most {@code limit}.
 */
public final class RunnerDetailsModel {

    private RunnerDetailsModel() {
    }

    /** {@code recent} newest first, as {@code findByRunnerIdAndStartedAtGreaterThanOrderByStartedAtDesc} gives them. */
    public static List<RunnerPrGroup> groups(List<JobExecution> recent, int limit) {
        record Key(String repo, Integer pr, String ref) {
        }
        Map<Key, List<JobView>> groups = new LinkedHashMap<>();
        for (JobExecution e : recent) {
            Key key = e.getPrNumber() != null ? new Key(e.getRepo(), e.getPrNumber(), null)
                    : new Key(e.getRepo(), null, e.getRef());
            if (!groups.containsKey(key) && groups.size() == limit) {
                continue;
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(view(e));
        }
        return groups.entrySet().stream()
                .map(g -> new RunnerPrGroup(g.getKey().repo(), g.getKey().pr(), g.getValue().getFirst().ref(),
                        g.getValue()))
                .toList();
    }

    private static JobView view(JobExecution e) {
        return new JobView(e.getTaskId(), e.getJobId() == null ? 0 : e.getJobId(), e.getRepo(), e.getPrNumber(),
                e.getRef(), e.getJobName(), e.getStatus(), e.getStartedAt(), e.getHtmlUrl(), e.getRunnerId(), null);
    }
}
