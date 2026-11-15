package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.RunnerRow;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.ui.model.PullDetails;
import io.vidocq.tools.dashboard.ui.model.PullDetailsModel;
import io.vidocq.tools.dashboard.ui.model.PullJobLine;
import io.vidocq.tools.dashboard.ui.model.StatusLabel;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * The right-hand panel of a PR: its CI contexts, its Sonar gate and the jobs of its last run with the runner of
 * each, a known runner opening its own drawer. It follows the PR while open (PR and runner changes of the org).
 */
public class PullDetailsDrawer extends Div {

    private final Messages messages = Texts.current();

    private final LiveState live;
    private final JobExecutionRepository jobs;
    private final RunnerRowRepository runners;
    private final Clock clock;
    private final BiConsumer<String, Long> openRunner;
    private volatile String org;
    private volatile String repo;
    private volatile int number;

    PullDetailsDrawer(LiveState live, ChangeBus bus, JobExecutionRepository jobs, RunnerRowRepository runners,
            Clock clock, BiConsumer<String, Long> openRunner) {
        this.live = live;
        this.jobs = jobs;
        this.runners = runners;
        this.clock = clock;
        this.openRunner = openRunner;
        Parts.drawer(this);
        LiveBinding.bind(this, bus, e -> isVisible() && (e.equals(new ChangeEvent.PullsChanged(org))
                || e.equals(new ChangeEvent.RunnersChanged(org))), this::refresh);
    }

    void open(String org, PullView pr) {
        this.org = org;
        this.repo = pr.repo();
        this.number = pr.number();
        setVisible(true);
        refresh();
    }

    void close() {
        setVisible(false);
        removeAll();
    }

    private void refresh() {
        removeAll();
        add(Parts.drawerTop(repo + " #" + number, this::close, messages));
        Optional<PullView> pr = live.snapshot(org).repos().stream().filter(r -> r.name().equals(repo))
                .flatMap(r -> r.pulls().stream()).filter(p -> p.number() == number).findFirst();
        if (pr.isEmpty()) {
            add(new Span(messages.text("pull.closed")));
            return;
        }
        var names = runners.findByOrg(org).stream()
                .collect(Collectors.toMap(RunnerRow::getId, RunnerRow::getName, (a, b) -> a));
        Map<Long, Duration> usual = new HashMap<>();
        live.snapshot(org).runners().stream().flatMap(r -> r.running().stream()).filter(j -> j.usual() != null)
                .forEach(j -> usual.put(j.taskId(), j.usual()));
        PullDetails d = PullDetailsModel.of(pr.get(),
                jobs.findByOrgAndRepoAndPrNumberOrderByStartedAtDesc(org, repo, number), names, usual,
                clock.instant(), messages);
        add(new Div(new Span(d.title())));
        add(Parts.line(Badges.dot(d.state(), messages),
                new Span(messages.text("pull.stateBy", Badges.label(d.state(), messages), d.author())),
                Parts.link(messages.text("pull.openOnCodefloe"), d.htmlUrl())));

        add(Parts.section(messages.text("pull.checks")));
        if (d.contexts().isEmpty()) {
            add(Parts.muted(messages.text("pull.noStatus")));
        }
        d.contexts().forEach(c -> add(Parts.line(Badges.dot(c.state(), messages),
                c.targetUrl() == null ? new Span(c.context()) : Parts.link(c.context(), c.targetUrl()))));
        add(Parts.line(new Span(messages.text("pull.sonar")), Badges.gate(d.sonarGate())));

        add(Parts.section(messages.text("pull.lastRunJobs")));
        if (d.lastRunJobs().isEmpty()) {
            add(Parts.muted(messages.text("pull.noJobs")));
        }
        d.lastRunJobs().forEach(j -> add(jobLine(j)));
    }

    private Component jobLine(PullJobLine j) {
        String at = org;
        Component runner = j.runnerId() == null ? new Span(j.runnerName())
                : Parts.action(j.runnerName(), () -> openRunner.accept(at, j.runnerId()));
        return Parts.line(Parts.link(j.jobName(), j.htmlUrl()), new Span(messages.text("pull.on")), runner,
                Parts.muted(StatusLabel.of(j.status(), messages) + " · " + j.duration()));
    }
}
