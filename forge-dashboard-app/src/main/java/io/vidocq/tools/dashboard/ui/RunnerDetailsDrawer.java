package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.domain.Formats;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RunnerView;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.ui.model.RunnerCard;
import io.vidocq.tools.dashboard.ui.model.RunnerDetailsModel;
import io.vidocq.tools.dashboard.ui.model.RunnerPrGroup;
import io.vidocq.tools.dashboard.ui.model.StatusLabel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The right-hand panel of a runner: its jobs in progress, then its jobs of the last 7 days grouped by PR (or pushed
 * ref), each linked to CodeFloe; an open PR opens its own drawer. It follows the runner while open.
 */
public class RunnerDetailsDrawer extends Div {

    private final Messages messages = Texts.current();

    static final Duration RECENT = Duration.ofDays(7);
    static final int GROUPS = 20;

    private final LiveState live;
    private final JobExecutionRepository jobs;
    private final Clock clock;
    private final BiConsumer<String, PullView> openPull;
    private volatile String org;
    private volatile long runnerId;

    RunnerDetailsDrawer(LiveState live, ChangeBus bus, JobExecutionRepository jobs, Clock clock,
            BiConsumer<String, PullView> openPull) {
        this.live = live;
        this.jobs = jobs;
        this.clock = clock;
        this.openPull = openPull;
        Parts.drawer(this);
        LiveBinding.bind(this, bus, e -> isVisible() && e.equals(new ChangeEvent.RunnersChanged(org)), this::refresh);
    }

    void open(String org, long runnerId) {
        this.org = org;
        this.runnerId = runnerId;
        setVisible(true);
        refresh();
    }

    void close() {
        setVisible(false);
        removeAll();
    }

    private void refresh() {
        removeAll();
        Optional<RunnerView> runner = live.snapshot(org).runners().stream().filter(r -> r.id() == runnerId).findFirst();
        add(Parts.drawerTop(runner.map(RunnerView::name).orElseGet(() -> messages.text("runner.title", String.valueOf(runnerId))),
                this::close, messages));
        if (runner.isEmpty()) {
            add(new Span(messages.text("runner.gone")));
            return;
        }
        RunnerView r = runner.get();
        Instant now = clock.instant();
        add(Parts.line(new Span(RunnerCard.label(r.status(), messages)),
                Parts.muted(r.running().size() + "/" + r.capacity() + " · " + String.join(", ", r.labels()))));

        add(Parts.section(messages.text("runner.running", r.running().size())));
        if (r.running().isEmpty()) {
            add(Parts.muted(messages.text("runner.noJob")));
        }
        r.running().forEach(j -> add(Parts.line(target(j.repo(), j.prNumber(), j.ref()),
                Parts.link(j.jobName(), j.htmlUrl()),
                Parts.muted(j.startedAt() == null ? "" : messages.text("runner.since",
                        Formats.duration(Duration.between(j.startedAt(), now), messages))))));

        add(Parts.section(messages.text("runner.recent", messages.text("duration.days", 7))));
        var groups = RunnerDetailsModel.groups(
                jobs.findByRunnerIdAndStartedAtGreaterThanOrderByStartedAtDesc(runnerId, now.minus(RECENT)), GROUPS);
        if (groups.isEmpty()) {
            add(Parts.muted(messages.text("runner.noRecent", 7)));
        }
        groups.forEach(g -> add(group(g, now)));
    }

    private Component group(RunnerPrGroup g, Instant now) {
        Div group = new Div(Parts.line(target(g.repo(), g.prNumber(), g.ref()),
                Parts.muted(messages.text("runner.jobs", g.jobs().size()))));
        group.getStyle().set("margin-bottom", "var(--lumo-space-s)");
        for (JobView j : g.jobs()) {
            Div line = new Div(Parts.line(Parts.link(j.jobName() == null ? "job" : j.jobName(), j.htmlUrl()),
                    Parts.muted(StatusLabel.of(j.status(), messages)
                            + (j.startedAt() == null ? "" : " · " + Formats.relative(j.startedAt(), now, messages)))));
            line.getStyle().set("padding-left", "var(--lumo-space-m)");
            group.add(line);
        }
        return group;
    }

    /** {@code repo #298} opening the PR drawer while the PR is open; {@code repo · main} for a push. */
    private Component target(String repo, Integer prNumber, String ref) {
        if (prNumber == null) {
            return new Span(repo + " · " + (ref == null ? "?" : ref.replaceFirst("^refs/heads/", "")));
        }
        String at = org;
        Optional<PullView> pr = live.snapshot(org).repos().stream().filter(r -> r.name().equals(repo))
                .flatMap(r -> r.pulls().stream()).filter(p -> p.number() == prNumber).findFirst();
        return pr.<Component>map(p -> Parts.action(repo + " #" + prNumber + " " + p.title(), () -> openPull.accept(at, p)))
                .orElseGet(() -> new Span(messages.text("runner.closedPull", repo, String.valueOf(prNumber))));
    }
}
