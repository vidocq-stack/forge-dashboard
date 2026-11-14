package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.html.Div;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;

import java.time.Clock;

/** The PR and runner drawers of a panel, each leading to the other; one open at a time. */
final class Drawers extends Div {

    private final PullDetailsDrawer pull;
    private final RunnerDetailsDrawer runner;

    Drawers(LiveState live, ChangeBus bus, JobExecutionRepository jobs, RunnerRowRepository runners, Clock clock) {
        pull = new PullDetailsDrawer(live, bus, jobs, runners, clock, this::openRunner);
        runner = new RunnerDetailsDrawer(live, bus, jobs, clock, this::openPull);
        add(pull, runner);
    }

    void openPull(String org, PullView pr) {
        runner.close();
        pull.open(org, pr);
    }

    void openRunner(String org, long runnerId) {
        pull.close();
        runner.open(org, runnerId);
    }
}
