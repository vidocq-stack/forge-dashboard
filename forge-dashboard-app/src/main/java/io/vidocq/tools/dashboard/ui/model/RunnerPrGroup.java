package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.live.JobView;

import java.util.List;

/** The recent jobs of a runner for one PR ({@code prNumber} set) or one pushed ref ({@code prNumber} null). */
public record RunnerPrGroup(String repo, Integer prNumber, String ref, List<JobView> jobs) {

    public RunnerPrGroup {
        jobs = List.copyOf(jobs);
    }
}
