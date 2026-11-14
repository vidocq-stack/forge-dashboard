package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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

import java.time.Clock;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The right-hand panel of a PR: its CI contexts, its Sonar gate and the jobs of its last run with the runner of
 * each. It follows the PR while open (PR and runner changes of the org, by push).
 */
public class PullDetailsDrawer extends Div {

    private final LiveState live;
    private final JobExecutionRepository jobs;
    private final RunnerRowRepository runners;
    private final Clock clock;
    private final Div body = new Div();
    private volatile String org;
    private volatile String repo;
    private volatile int number;

    PullDetailsDrawer(LiveState live, ChangeBus bus, JobExecutionRepository jobs, RunnerRowRepository runners,
            Clock clock) {
        this.live = live;
        this.jobs = jobs;
        this.runners = runners;
        this.clock = clock;
        getStyle().set("position", "fixed").set("top", "0").set("right", "0").set("height", "100%")
                .set("width", "min(30rem, 100%)").set("box-sizing", "border-box").set("overflow-y", "auto")
                .set("padding", "var(--lumo-space-m)").set("z-index", "100")
                .set("background", "var(--lumo-base-color)").set("box-shadow", "var(--lumo-box-shadow-l)");
        add(body);
        setVisible(false);
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
        body.removeAll();
    }

    private void refresh() {
        body.removeAll();
        Button close = new Button("✕", e -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        close.getElement().setAttribute("aria-label", "Fermer");
        Optional<PullView> pr = live.snapshot(org).repos().stream().filter(r -> r.name().equals(repo))
                .flatMap(r -> r.pulls().stream()).filter(p -> p.number() == number).findFirst();
        H3 heading = new H3(repo + " #" + number);
        heading.getStyle().set("margin", "0").set("flex", "1");
        HorizontalLayout top = new HorizontalLayout(heading, close);
        top.setWidthFull();
        top.setAlignItems(FlexComponent.Alignment.CENTER);
        body.add(top);
        if (pr.isEmpty()) {
            body.add(new Span("Cette PR n'est plus ouverte."));
            return;
        }
        var names = runners.findByOrg(org).stream()
                .collect(Collectors.toMap(RunnerRow::getId, RunnerRow::getName, (a, b) -> a));
        PullDetails d = PullDetailsModel.of(pr.get(),
                jobs.findByOrgAndRepoAndPrNumberOrderByStartedAtDesc(org, repo, number), names, clock.instant());
        body.add(new Div(new Span(d.title())));
        body.add(line(Badges.dot(d.state()), new Span(Badges.label(d.state()) + " · par " + d.author()),
                link("Ouvrir sur CodeFloe", d.htmlUrl())));

        body.add(section("Contrôles CI"));
        if (d.contexts().isEmpty()) {
            body.add(muted("Aucun statut."));
        }
        d.contexts().forEach(c -> body.add(line(Badges.dot(c.state()),
                c.targetUrl() == null ? new Span(c.context()) : link(c.context(), c.targetUrl()))));
        body.add(line(new Span("Sonar :"), Badges.gate(d.sonarGate())));

        body.add(section("Jobs du dernier run"));
        if (d.lastRunJobs().isEmpty()) {
            body.add(muted("Aucun job vu par le dashboard."));
        }
        d.lastRunJobs().forEach(j -> body.add(jobLine(j)));
    }

    private static Component jobLine(PullJobLine j) {
        Span runner = new Span(j.runnerName());
        runner.getStyle().set("font-weight", "600");
        runner.getElement().setAttribute("title", "runner");
        return line(link(j.jobName(), j.htmlUrl()), new Span("sur"), runner,
                muted(j.status() + " · " + j.duration()));
    }

    private static HorizontalLayout line(Component... parts) {
        HorizontalLayout line = new HorizontalLayout(parts);
        line.setAlignItems(FlexComponent.Alignment.BASELINE);
        line.setSpacing(false);
        line.getStyle().set("gap", "var(--lumo-space-s)").set("flex-wrap", "wrap");
        return line;
    }

    private static H4 section(String title) {
        H4 h = new H4(title);
        h.getStyle().set("margin", "var(--lumo-space-m) 0 var(--lumo-space-xs)");
        return h;
    }

    private static Span muted(String text) {
        Span s = new Span(text);
        s.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return s;
    }

    static Anchor link(String text, String url) {
        Anchor a = new Anchor(url, text);
        a.setTarget(AnchorTarget.BLANK);
        return a;
    }
}
