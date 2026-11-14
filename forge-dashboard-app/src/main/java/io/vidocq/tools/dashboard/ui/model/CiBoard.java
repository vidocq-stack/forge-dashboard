package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.live.JobView;

import java.util.List;

/** The CI view of an org: its runners, offline ones last, and the jobs waiting for one, oldest first. */
public record CiBoard(List<RunnerCard> runners, List<JobView> waiting, int waitingCount) {

    public CiBoard {
        runners = List.copyOf(runners);
        waiting = List.copyOf(waiting);
    }
}
