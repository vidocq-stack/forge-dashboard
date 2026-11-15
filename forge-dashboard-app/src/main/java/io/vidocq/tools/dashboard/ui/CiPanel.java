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

    private static final Map<String, Duration> WINDOWS = new LinkedHashMap<>();

    static {
        WINDOWS.put("1 h", Duration.ofHours(1));
        WINDOWS.put("24 h", Duration.ofHours(24));
        WINDOWS.put("7 j", Duration.ofDays(7));
    }

    private final LiveState live;
    private final ChangeBus bus;
    private final OccupancyService occupancy;
    private final Clock clock;
    private final Drawers drawers;
    private final Map<String, Button> windowButtons = new LinkedHashMap<>();
    private final Div cards = new Div();
    private final Div queue = new Div();
    private String window = "24 h";
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
        HorizontalLayout windows = new HorizontalLayout(new Span("Occupation sur"));
        windows.setAlignItems(HorizontalLayout.Alignment.CENTER);
        windows.setSpacing(false);
        windows.getStyle().set("gap", "2px");
        WINDOWS.keySet().forEach(label -> {
            Button b = new Button(label, e -> {
                window = label;
                markWindow();
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_SMALL);
            windowButtons.put(label, b);
            windows.add(b);
        });
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

    private void markWindow() {
        windowButtons.forEach((label, b) -> {
            b.removeThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
            b.addThemeVariants(label.equals(window) ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
        });
    }

    private void refresh() {
        CiBoard board = CiBoardModel.of(live.snapshot(org), occupancy.occupancy(org, WINDOWS.get(window)),
                occupancy.hourly(org));
        Instant now = clock.instant();
        cards.removeAll();
        if (board.runners().isEmpty()) {
            cards.add(new Span("Aucun runner pour " + org + " pour l'instant : la collecte arrive, ou le jeton ne "
                    + "lit pas les runners de cette org."));
        }
        board.runners().forEach(c -> cards.add(card(c, now)));

        queue.removeAll();
        queue.add(Parts.section("File d'attente (" + board.waitingCount() + ")"));
        if (board.waiting().isEmpty()) {
            queue.add(Parts.muted("Aucun job en attente."));
        }
        board.waiting().forEach(j -> queue.add(Parts.line(target(j), Parts.link(j.jobName(), j.htmlUrl()),
                Parts.muted(j.startedAt() == null ? "" : "en attente depuis "
                        + Formats.duration(Duration.between(j.startedAt(), now), messages)))));
    }

    private Component card(RunnerCard c, Instant now) {
        Div card = new Div();
        card.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)").set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
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
        card.add(Parts.line(new Span("Charge " + c.load()),
                Parts.muted("Occupation " + window + " : " + percent(c.occupancy()))));
        Html spark = new Html("<span title=\"Occupation heure par heure, 24 h\" style=\"color:var(--lumo-primary-color)\">"
                + Sparkline.svg(c.hourlyOccupancy(), 240, 28) + "</span>");
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
