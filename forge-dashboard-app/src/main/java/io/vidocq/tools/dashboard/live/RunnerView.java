package io.vidocq.tools.dashboard.live;

import java.util.List;

/** A runner, its slots and the jobs it is running. */
public record RunnerView(long id, String name, String status, List<String> labels, int capacity,
                         List<JobView> running) {

    public RunnerView {
        labels = List.copyOf(labels);
        running = List.copyOf(running);
    }
}
