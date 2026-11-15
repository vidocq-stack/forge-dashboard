package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.collect.OccupancyService;
import io.vidocq.tools.dashboard.domain.Formats;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.ui.model.CiBoard;
import io.vidocq.tools.dashboard.ui.model.CiBoardModel;
import io.vidocq.tools.dashboard.ui.model.RunnerCard;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The runners of an org: status, jobs in progress against capacity, occupancy over 1 h, 24 h or 7 days and hour by
 * hour over 24 h; then the jobs waiting for a runner, oldest first. A runner opens its drawer, a PR its own.
 * Refreshed by push on the runner changes of the org.
 */
@Dependent
public class CiPanel extends Div {

    private final Messages messages = Texts.current();

    private static final List<Duration> WINDOWS = List.of(Duration.ofHours(1), Duration.ofHours(24), Duration.ofDays(7));

    private final LiveState live;
    private final ChangeBus bus;
    private final OccupancyService occupancy;
    private final Clock clock;
    private final Drawers drawers;
    private final Map<Duration, Button> windowButtons = new LinkedHashMap<>();
    private final Div cards = new Div();
    private final Div queue = new Div();
    private Duration window = Duration.ofHours(24);
    private String org;

    @Inject
    public CiPanel(LiveState live, ChangeBus bus, OccupancyService occupancy, JobExecutionRepository jobs,
            RunnerRowRepository runners, Clock clock) {
        this.live = live;
        this.bus = bus;
        this.occupancy = occupancy;
        this.clock = clock;
        this.drawers = new Drawers(live, bus, jobs, runners, clock);
        setWidthFull();
        HorizontalLayout windows = new HorizontalLayout(new Span(messages.text("ci.occupancyOver")));
        windows.setAlignItems(HorizontalLayout.Alignment.CENTER);
        windows.setSpacing(false);
        windows.getStyle().set("gap", "var(--lumo-space-s)");
        HorizontalLayout group = Parts.toggle();
        WINDOWS.forEach(span -> {
            Button b = new Button(label(span), e -> {
                window = span;
                markWindow();
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_SMALL);
            windowButtons.put(span, b);
            group.add(b);
        });
        windows.add(group);
        markWindow();
        cards.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("gap", "var(--lumo-space-m)")
                .set("margin", "var(--lumo-space-m) 0");
        add(windows, cards, queue, drawers);
    }

    /** Shows {@code org}; called once, right after creation. */
    public CiPanel forOrg(String org) {
        this.org = org;
        refresh();
        LiveBinding.bind(this, bus, e -> e.equals(new ChangeEvent.RunnersChanged(org)), this::refresh);
        return this;
    }

    /** {@code 1 h}, {@code 24 h}, {@code 7 d}: hours up to a day, then days. */
    private String label(Duration span) {
        return span.toHours() <= 24 ? messages.text("duration.hours", span.toHours())
                : messages.text("duration.days", span.toDays());
    }

    private void markWindow() {
        windowButtons.forEach((span, b) -> {
            b.removeThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
            b.addThemeVariants(span.equals(window) ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
        });
    }

    private void refresh() {
        CiBoard board = CiBoardModel.of(live.snapshot(org), occupancy.occupancy(org, window),
                occupancy.hourly(org));
        Instant now = clock.instant();
        cards.removeAll();
        if (board.runners().isEmpty()) {
            cards.add(new Span(messages.text("ci.noRunners", org)));
        }
        board.runners().forEach(c -> cards.add(card(c, now)));

        queue.removeAll();
        queue.add(Parts.section(messages.text("ci.queue", board.waitingCount())));
        if (board.waiting().isEmpty()) {
            queue.add(Parts.muted(messages.text("ci.queueEmpty")));
        }
        board.waiting().forEach(j -> queue.add(Parts.line(target(j), Parts.link(j.jobName(), j.htmlUrl()),
                Parts.muted(j.startedAt() == null ? "" : messages.text("ci.waitingSince",
                        Formats.duration(Duration.between(j.startedAt(), now), messages))))));
    }

    private Component card(RunnerCard c, Instant now) {
        Div card = new Div();
        card.addClassName(Theme.CARD);
        card.getStyle().set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("width", "18rem").set("box-sizing", "border-box")
                .set("opacity", c.offline() ? "0.6" : "1");
        Button name = Parts.action(c.name(), () -> drawers.openRunner(org, c.runnerId()));
        name.getStyle().set("font-weight", "600");
        Span status = new Span(c.label(messages));
        status.getStyle().set("color", switch (c.status() == null ? "" : c.status()) {
            case "active" -> "var(--lumo-success-text-color)";
            case "offline" -> "var(--lumo-error-text-color)";
            default -> "var(--lumo-secondary-text-color)";
        });
        card.add(Parts.line(name, status));
        card.add(Parts.line(new Span(messages.text("ci.load", c.load())),
                Parts.muted(messages.text("ci.occupancy", label(window), percent(c.occupancy())))));
        Span spark = new Span(new Html(Sparkline.svg(c.hourlyOccupancy(), 240, 28)));
        spark.getElement().setAttribute("title", messages.text("ci.hourly"));
        spark.getStyle().set("color", "var(--lumo-primary-color)");
        card.add(spark);
        c.running().forEach(j -> card.add(Parts.line(target(j), Parts.muted(j.jobName() + (j.startedAt() == null ? ""
                : " · " + Formats.duration(Duration.between(j.startedAt(), now), messages))))));
        return card;
    }

    /** {@code repo #298} opening the PR drawer while the PR is open, else plain text. */
    private Component target(JobView j) {
        if (j.prNumber() == null) {
            return new Span(j.repo() + " · " + (j.ref() == null ? "?" : j.ref().replaceFirst("^refs/heads/", "")));
        }
        Optional<PullView> pr = live.snapshot(org).repos().stream().filter(r -> r.name().equals(j.repo()))
                .flatMap(r -> r.pulls().stream()).filter(p -> p.number() == j.prNumber()).findFirst();
        String label = j.repo() + " #" + j.prNumber();
        return pr.<Component>map(p -> Parts.action(label, () -> drawers.openPull(org, p)))
                .orElseGet(() -> new Span(label));
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.0f %%", ratio * 100);
    }
}
