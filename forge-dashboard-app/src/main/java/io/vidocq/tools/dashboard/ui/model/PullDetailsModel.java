package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.Formats;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.store.JobExecution;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The PR drawer from the PR and its job executions (most recent first, as the repository gives them). */
public final class PullDetailsModel {

    private PullDetailsModel() {
    }

    public static PullDetails of(PullView pr, List<JobExecution> jobs, Map<Long, String> runnerNames, Instant now,
                                 Messages m) {
        List<PullJobLine> lastRun = jobs.isEmpty() ? List.of() : jobs.stream()
                .filter(j -> Objects.equals(j.getRunId(), jobs.getFirst().getRunId()))
                .sorted(Comparator.comparing(JobExecution::getStartedAt))
                .map(j -> line(j, runnerNames, now, m))
                .toList();
        return new PullDetails(pr.title(), pr.author(), pr.state(), pr.contexts(), pr.sonarGate(), lastRun,
                pr.htmlUrl());
    }

    private static PullJobLine line(JobExecution j, Map<Long, String> runnerNames, Instant now, Messages m) {
        String runner = j.getRunnerId() == null ? null : runnerNames.get(j.getRunnerId());
        Instant end = j.getStoppedAt() == null ? now : j.getStoppedAt();
        return new PullJobLine(j.getJobName(), runner == null ? null : j.getRunnerId(), runner == null ? "?" : runner,
                j.getStatus(), Formats.duration(Duration.between(j.getStartedAt(), end), m), j.getHtmlUrl());
    }
}
