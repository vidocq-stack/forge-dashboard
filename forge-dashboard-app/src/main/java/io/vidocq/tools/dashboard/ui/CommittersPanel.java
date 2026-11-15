package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.domain.Formats;
import io.vidocq.tools.dashboard.forge.ForgeLinks;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.store.CommitStatRepository;
import io.vidocq.tools.dashboard.store.StatsQueries;
import io.vidocq.tools.dashboard.ui.model.CommitterRow;
import io.vidocq.tools.dashboard.ui.model.CommittersModel;
import io.vidocq.tools.dashboard.ui.model.CommittersView;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Who committed over 7, 30 or 90 days, in the current org or in all: people with the share of their commits made
 * with each agent family, then the bot accounts, then the agent commits nobody could be found for. A person opens
 * their recent commits. Refreshed by push when commits are collected.
 */
@Dependent
public class CommittersPanel extends Div {

    static final List<Integer> PERIODS = List.of(7, 30, 90);
    private static final Map<String, String> COLOURS = Map.of(
            CommitterRow.ALONE, "var(--lumo-contrast-40pct)",
            "Claude Opus", "#d97757", "Claude Sonnet", "#e8a33d", "Claude Fable", "#9c5b2e",
            "Claude Haiku", "#f0c38e", "Claude", "#c08457", "Codex", "#10a37f", "Copilot", "#6e40c9",
            "Gemini", "#4285f4", "Mistral Vibe", "#c0392b");

    private final Messages messages = Texts.current();
    private final StatsQueries stats;
    private final ChangeBus bus;
    private final Clock clock;
    private final CommitterDetailsDrawer drawer;
    private final Map<Integer, Button> periodButtons = new LinkedHashMap<>();
    private final Checkbox allOrgs = new Checkbox(messages.text("committers.allOrgs"));
    private final Div body = new Div();
    private String org;
    private int periodDays;
    private IntConsumer savePeriod;
    private boolean narrow;

    @Inject
    public CommittersPanel(StatsQueries stats, CommitStatRepository commits, ForgeLinks links, ChangeBus bus,
            Clock clock) {
        this.stats = stats;
        this.bus = bus;
        this.clock = clock;
        this.drawer = new CommitterDetailsDrawer(commits, links, clock);
        setWidthFull();
        HorizontalLayout controls = new HorizontalLayout(new Span(messages.text("committers.period")));
        controls.setAlignItems(FlexComponent.Alignment.CENTER);
        controls.getStyle().set("gap", "2px").set("flex-wrap", "wrap");
        controls.setSpacing(false);
        for (int days : PERIODS) {
            Button b = new Button(messages.text("duration.days", days), e -> {
                periodDays = days;
                savePeriod.accept(days);
                markPeriod();
                refresh();
            });
            b.addThemeVariants(ButtonVariant.LUMO_SMALL);
            periodButtons.put(days, b);
            controls.add(b);
        }
        allOrgs.getStyle().set("margin-left", "var(--lumo-space-m)");
        allOrgs.addValueChangeListener(e -> refresh());
        controls.add(allOrgs);
        add(controls, body, drawer);
    }

    /** Shows {@code org} over {@code periodDays}; a period change is handed to {@code savePeriod}. */
    public CommittersPanel forOrg(String org, int periodDays, IntConsumer savePeriod) {
        this.org = org;
        this.periodDays = periodDays;
        this.savePeriod = savePeriod;
        markPeriod();
        refresh();
        LiveBinding.bind(this, bus, e -> e instanceof ChangeEvent.CommitsChanged, this::refresh);
        return this;
    }

    /** On a narrow screen, only who, commits and share; may be called before {@link #forOrg}. */
    CommittersPanel narrow(boolean narrow) {
        if (narrow != this.narrow) {
            this.narrow = narrow;
            if (org != null) {
                refresh();
            }
        }
        return this;
    }

    private void markPeriod() {
        periodButtons.forEach((days, b) -> {
            b.removeThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
            b.addThemeVariants(days == periodDays ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
        });
    }

    private void refresh() {
        Optional<String> scope = allOrgs.getValue() ? Optional.empty() : Optional.of(org);
        Instant from = clock.instant().minus(Duration.ofDays(periodDays));
        CommittersView view = CommittersModel.of(stats.committerAggregates(scope, from), stats.prCounts(scope, from));
        long total = Stream.concat(Stream.concat(view.humans().stream(), view.automation().stream()),
                        Stream.ofNullable(view.unattributed()))
                .mapToLong(CommitterRow::commits).sum();
        body.removeAll();
        if (total == 0) {
            body.add(new Span(scope.map(o -> messages.text("committers.empty", periodDays, o))
                    .orElseGet(() -> messages.text("committers.emptyAllOrgs", periodDays))));
            return;
        }
        body.add(Parts.section(messages.text("committers.humans")), grid(view.humans(), total, true));
        if (!narrow) {
            body.add(legend(view.humans()));
        }
        if (view.unattributed() != null) {
            body.add(Parts.muted(messages.text("committers.unattributed", view.unattributed().commits())));
        }
        if (!view.automation().isEmpty()) {
            body.add(Parts.section(messages.text("committers.automation")), grid(view.automation(), total, false));
        }
    }

    private Component grid(List<CommitterRow> rows, long total, boolean people) {
        Grid<CommitterRow> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.setAllRowsVisible(true);
        grid.addComponentColumn(r -> people
                        ? Parts.action(r.humanKey(), () -> drawer.open(r.humanKey(), allOrgs.getValue() ? null : org,
                        periodDays))
                        : new Span(r.humanKey()))
                .setHeader(messages.text(people ? "committers.column.committer" : "committers.column.account")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(r -> Long.toString(r.commits())).setHeader(messages.text("committers.column.commits")).setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(r -> percent(r.commits(), total)).setHeader(messages.text("committers.column.share")).setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        if (!narrow) {
            grid.addColumn(r -> "+" + Formats.compact(r.additions()) + " / −" + Formats.compact(r.deletions()))
                    .setHeader(messages.text("committers.column.lines")).setAutoWidth(true).setFlexGrow(0);
            if (people) {
                grid.addColumn(r -> r.prsOpened() + " / " + r.prsMerged()).setHeader(messages.text("committers.column.pulls"))
                        .setAutoWidth(true).setFlexGrow(0);
                grid.addComponentColumn(this::bar).setHeader(messages.text("committers.column.agents")).setWidth("14rem")
                        .setFlexGrow(0);
            }
        }
        grid.setItems(rows);
        return grid;
    }

    /** One segment per part, its width the share of the part; several agents on a commit count in each. */
    private Component bar(CommitterRow r) {
        long scale = Math.max(r.commits(), r.breakdownTotal());
        Div bar = new Div();
        bar.getStyle().set("display", "flex").set("width", "12rem").set("height", "0.8rem")
                .set("border-radius", "var(--lumo-border-radius-s)").set("overflow", "hidden")
                .set("background", "var(--lumo-contrast-5pct)");
        r.breakdown().forEach((part, commits) -> {
            Div segment = new Div();
            segment.getStyle().set("width", String.format(Locale.ROOT, "%.2f%%", 100.0 * commits / scale))
                    .set("background", colour(part));
            bar.add(segment);
        });
        String parts = r.breakdown().entrySet().stream().map(e -> part(e.getKey()) + " " + e.getValue())
                .collect(Collectors.joining(" · "));
        String note = r.breakdownTotal() > r.commits()
                ? "\n" + messages.text("committers.multipleAgents") : "";
        bar.getElement().setAttribute("title", parts + note + "\n" + messages.text("committers.agentShareNote"));
        return bar;
    }

    private Component legend(List<CommitterRow> rows) {
        Set<String> parts = new LinkedHashSet<>();
        rows.forEach(r -> parts.addAll(r.breakdown().keySet()));
        HorizontalLayout legend = new HorizontalLayout();
        legend.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-m)")
                .set("font-size", "var(--lumo-font-size-s)").set("margin-top", "var(--lumo-space-xs)");
        legend.setSpacing(false);
        parts.forEach(p -> {
            Span swatch = new Span("■");
            swatch.getStyle().set("color", colour(p));
            legend.add(new Span(swatch, new Span(" " + part(p))));
        });
        legend.getElement().setAttribute("title", messages.text("committers.agentShareNote"));
        return legend;
    }

    /** A part of the commits as shown: an agent family as is, the commits without agent in the user's language. */
    private String part(String key) {
        return CommitterRow.ALONE.equals(key) ? messages.text("committers.alone") : key;
    }

    static String colour(String part) {
        return COLOURS.getOrDefault(part, "var(--lumo-contrast-60pct)");
    }

    private static String percent(long part, long total) {
        return total == 0 ? "—" : String.format(Locale.ROOT, "%.0f %%", 100.0 * part / total);
    }
}
