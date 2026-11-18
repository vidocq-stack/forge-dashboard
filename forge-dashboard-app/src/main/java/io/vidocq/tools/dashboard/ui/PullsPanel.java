package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.ui.model.ProjectsModel;
import io.vidocq.tools.dashboard.ui.model.ProjectsProgress;
import io.vidocq.tools.dashboard.ui.model.PullRow;
import io.vidocq.tools.dashboard.ui.model.PullsModel;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The open PRs of an org, every project together: state, project, PR (it opens its drawer), author, Sonar gate, last
 * change, progress of the running build. Refreshed by push on the PR and Sonar changes of the org; the progress moves
 * in place with the runners.
 */
@Dependent
public class PullsPanel extends Div {

    private final Messages messages = Texts.current();

    private final LiveState live;
    private final ChangeBus bus;
    private final Clock clock;
    private final Grid<PullRow> grid = new Grid<>();
    private final Drawers drawers;
    private final List<Grid.Column<PullRow>> wideOnly = new ArrayList<>();
    /** The PRs on screen, in their order: the grid reloads only when they change. */
    private List<PullView> shown;
    private ProjectsProgress progress = ProjectsProgress.NONE;
    /** The cells that move in place: the state dots, the last changes and the progress of the rows on screen. */
    private final Map<PullKey, Dot> dots = new HashMap<>();
    private final Map<PullKey, Span> updated = new HashMap<>();
    private final Map<PullKey, Span> builds = new HashMap<>();
    private String org;

    @Inject
    public PullsPanel(LiveState live, ChangeBus bus, JobExecutionRepository jobs, RunnerRowRepository runners,
            Clock clock) {
        this.live = live;
        this.bus = bus;
        this.clock = clock;
        this.drawers = new Drawers(live, bus, jobs, runners, clock);
        setWidthFull();

        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setPartNameGenerator(r -> r.error() ? Theme.ERROR_ROW : null);
        grid.setAllRowsVisible(true);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.addComponentColumn(this::dot).setHeader(messages.text("pulls.column.state")).setAutoWidth(true)
                .setFlexGrow(0);
        grid.addColumn(r -> r.pull().repo()).setHeader(messages.text("pulls.column.repo")).setAutoWidth(true)
                .setFlexGrow(0);
        // the room left, not the longest title's width: on a phone, a long title would push the grid off the screen.
        // Widths in rem: an em is each cell's, and the header's font is smaller than the rows'
        grid.addComponentColumn(this::pull).setHeader(messages.text("pulls.column.pull")).setWidth("10rem")
                .setFlexGrow(1);
        wideOnly.add(grid.addComponentColumn(r -> Parts.muted(r.pull().author()))
                .setHeader(messages.text("pulls.column.author")).setAutoWidth(true).setFlexGrow(0));
        wideOnly.add(grid.addComponentColumn(r -> Badges.gate(r.pull().sonarGate()))
                .setHeader(messages.text("pulls.column.gate")).setAutoWidth(true).setFlexGrow(0));
        // these two move in place, which does not measure the columns again: a width of their own, the longest text's
        // ("il y a 59 min", "10 min langer dan gewoonlijk"), or a build started after the first rendering is cut
        wideOnly.add(grid.addComponentColumn(this::updated).setHeader(messages.text("pulls.column.updated"))
                .setWidth("8.5rem").setFlexGrow(0));
        wideOnly.add(grid.addComponentColumn(this::build).setHeader(messages.text("pulls.column.build"))
                .setWidth("14.5rem").setFlexGrow(0));
        add(grid, drawers);
    }

    /** Shows {@code org}; called once, right after creation. */
    public PullsPanel forOrg(String org) {
        this.org = org;
        refresh();
        LiveBinding.bind(this, bus, ProjectsPanel.rowsInterest(org), this::refresh);
        LiveBinding.bind(this, bus, ProjectsPanel.progressInterest(org), this::refreshProgress);
        return this;
    }

    /** On a narrow screen, only the state, project and PR columns; may be called before {@link #forOrg}. */
    PullsPanel narrow(boolean narrow) {
        wideOnly.forEach(column -> column.setVisible(!narrow));
        grid.recalculateColumnWidths();
        return this;
    }

    /** Reloads the grid only when a PR changed: a reload loses the keyboard focus. The last changes move in place. */
    void refresh() {
        OrgSnapshot snapshot = live.snapshot(org);
        Instant now = clock.instant();
        progress = ProjectsModel.progress(snapshot, now, messages);
        List<PullRow> rows = PullsModel.rows(snapshot, now, messages);
        List<PullView> next = rows.stream().map(PullRow::pull).toList();
        if (next.equals(shown)) {
            rows.forEach(r -> {
                Span when = updated.get(PullKey.of(r.pull()));
                if (when != null) {
                    when.setText(r.updated());
                }
            });
            showProgress();
        } else {
            shown = next;
            // the grid renders its rows anew, with the progress just computed
            dots.clear();
            updated.clear();
            builds.clear();
            grid.setItems(rows);
            // the auto widths were measured with the first rows: a project with a longer name would be cut
            grid.recalculateColumnWidths();
        }
        if (rows.isEmpty()) {
            grid.setVisible(false);
            if (getChildren().noneMatch(Span.class::isInstance)) {
                addComponentAsFirst(new Span(messages.text("pulls.empty", org)));
            }
        } else {
            grid.setVisible(true);
            getChildren().filter(Span.class::isInstance).toList().forEach(this::remove);
        }
    }

    /** On each pass of the runners: the progress moves in the dots and the build cells; the grid is not reloaded. */
    void refreshProgress() {
        progress = ProjectsModel.progress(live.snapshot(org), clock.instant(), messages);
        showProgress();
    }

    private void showProgress() {
        dots.forEach((pr, d) -> Badges.describe(d.dot(), d.state(), progress.pull(pr.repo(), pr.number()), messages));
        builds.forEach((pr, span) -> show(span, progress.pull(pr.repo(), pr.number())));
    }

    private static void show(Span span, String progress) {
        span.setText(progress == null ? "" : progress);
        span.setVisible(progress != null);
    }

    private Span dot(PullRow row) {
        PullView pr = row.pull();
        Span dot = Badges.dot(pr.state(), progress.pull(pr.repo(), pr.number()), messages);
        dots.put(PullKey.of(pr), new Dot(dot, pr.state()));
        return dot;
    }

    /**
     * The component tags of the PR as neutral pills, then « #n short title », which opens the PR's drawer, then the
     * WIP pill of a draft. Short of room, the title shrinks to an ellipsis (the whole title in its tooltip); the pills
     * keep their size, and the line wraps.
     */
    private Component pull(PullRow row) {
        PullView pr = row.pull();
        String full = "#" + pr.number() + " " + pr.title();
        List<Component> parts = new ArrayList<>(pr.tags().size() + 3);
        pr.tags().forEach(tag -> parts.add(Badges.tag(tag, messages)));
        parts.add(Parts.title("#" + pr.number() + " " + pr.shortTitle(), full, () -> drawers.openPull(org, pr)));
        if (pr.draft()) {
            parts.add(Badges.wip(messages));
        }
        HorizontalLayout cell = new HorizontalLayout(parts.toArray(Component[]::new));
        cell.setAlignItems(FlexComponent.Alignment.CENTER);
        cell.getStyle().set("min-width", "0").set("flex-wrap", "wrap");
        return cell;
    }

    private Span updated(PullRow row) {
        Span when = Parts.muted(row.updated());
        updated.put(PullKey.of(row.pull()), when);
        return when;
    }

    private Span build(PullRow row) {
        PullView pr = row.pull();
        Span build = Parts.muted("");
        builds.put(PullKey.of(pr), build);
        show(build, progress.pull(pr.repo(), pr.number()));
        return build;
    }

    private record Dot(Span dot, CiState state) {
    }

    private record PullKey(String repo, int number) {
        static PullKey of(PullView pr) {
            return new PullKey(pr.repo(), pr.number());
        }
    }
}
