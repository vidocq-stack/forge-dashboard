package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.ui.model.ProjectRow;
import io.vidocq.tools.dashboard.ui.model.ProjectsModel;
import io.vidocq.tools.dashboard.ui.model.ProjectsProgress;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The projects of an org: state of main, Sonar figures, open PRs counted by state, last commit. The PR counter
 * unfolds the PRs of the repo; a PR opens its drawer. Refreshed by push on the PR and Sonar changes of the org.
 */
@Dependent
public class ProjectsPanel extends Div {

    private final Messages messages = Texts.current();

    private final LiveState live;
    private final ChangeBus bus;
    private final Clock clock;
    private final Grid<ProjectRow> grid = new Grid<>();
    private final Drawers drawers;
    private final Set<String> unfolded = new HashSet<>();
    private Shown shown;
    private ProjectsProgress progress = ProjectsProgress.NONE;
    /** The main dots and the progress of the PR lines on screen: the progress moves in place, without a reload. */
    private final Map<String, MainDot> mainDots = new HashMap<>();
    private final Map<PullKey, Span> pullProgress = new HashMap<>();
    private final List<Grid.Column<ProjectRow>> wideOnly = new ArrayList<>();
    private String org;

    @Inject
    public ProjectsPanel(LiveState live, ChangeBus bus, JobExecutionRepository jobs, RunnerRowRepository runners,
            Clock clock) {
        this.live = live;
        this.bus = bus;
        this.clock = clock;
        this.drawers = new Drawers(live, bus, jobs, runners, clock);
        setWidthFull();

        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        // projects in error stand out in red; separators rather than stripes, which the red tint would clash with
        grid.setPartNameGenerator(r -> r.error() ? Theme.ERROR_ROW : null);
        grid.setAllRowsVisible(true);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        // the room left, not the longest name's width: on a phone, "vidocq-workspace" pushed the PR pills off the
        // screen; a name too long for it ends in an ellipsis
        grid.addComponentColumn(r -> Parts.link(r.repo(), r.htmlUrl())).setHeader(messages.text("projects.column.repo"))
                .setWidth("6em").setFlexGrow(1);
        grid.addComponentColumn(this::mainDot).setHeader(messages.text("projects.column.main")).setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER);
        wideOnly.add(grid.addColumn(ProjectRow::loc).setHeader(messages.text("projects.column.loc")).setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END));
        wideOnly.add(grid.addColumn(ProjectRow::tests).setHeader(messages.text("projects.column.tests")).setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END));
        wideOnly.add(grid.addColumn(ProjectRow::coverage).setHeader(messages.text("projects.column.coverage")).setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END));
        grid.addComponentColumn(r -> Badges.gate(r.gate())).setHeader(messages.text("projects.column.gate")).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::pullCounter).setHeader(messages.text("projects.column.pulls")).setAutoWidth(true).setFlexGrow(0);
        wideOnly.add(grid.addColumn(ProjectRow::lastCommit).setHeader(messages.text("projects.column.lastCommit")).setAutoWidth(true)
                .setFlexGrow(0));
        grid.setDetailsVisibleOnClick(false);
        grid.setItemDetailsRenderer(new ComponentRenderer<>(this::pullList));
        add(grid, drawers);
    }

    /** Shows {@code org}; called once, right after creation. */
    public ProjectsPanel forOrg(String org) {
        this.org = org;
        refresh();
        LiveBinding.bind(this, bus, rowsInterest(org), this::refresh);
        LiveBinding.bind(this, bus, progressInterest(org), this::refreshProgress);
        return this;
    }

    /** On a narrow screen, only the repo, main, gate and PR columns; may be called before {@link #forOrg}. */
    ProjectsPanel narrow(boolean narrow) {
        wideOnly.forEach(column -> column.setVisible(!narrow));
        // the auto widths were measured with the other set of columns
        grid.recalculateColumnWidths();
        return this;
    }

    /** The rows change with the PRs and Sonar. */
    static Predicate<ChangeEvent> rowsInterest(String org) {
        return e -> e.equals(new ChangeEvent.PullsChanged(org)) || e.equals(new ChangeEvent.SonarChanged(org));
    }

    /** The progress of the running builds moves with each pass of the runners. */
    static Predicate<ChangeEvent> progressInterest(String org) {
        return e -> e.equals(new ChangeEvent.RunnersChanged(org));
    }

    /** Reloads the grid only when a row or a PR changed: a reload loses the keyboard focus. */
    void refresh() {
        OrgSnapshot snapshot = live.snapshot(org);
        Instant now = clock.instant();
        progress = ProjectsModel.progress(snapshot, now, messages);
        List<ProjectRow> rows = ProjectsModel.rows(snapshot, now, messages);
        Shown next = new Shown(rows, snapshot.repos().stream().map(RepoView::pulls).toList());
        if (next.equals(shown)) {
            showProgress();
        } else {
            shown = next;
            // the grid renders its rows and details anew, with the progress just computed
            mainDots.clear();
            pullProgress.clear();
            grid.setItems(rows);
            rows.stream().filter(r -> unfolded.contains(r.repo())).forEach(r -> grid.setDetailsVisible(r, true));
        }
        if (rows.isEmpty()) {
            grid.setVisible(false);
            if (getChildren().noneMatch(Span.class::isInstance)) {
                addComponentAsFirst(new Span(messages.text("projects.empty", org)));
            }
        } else {
            grid.setVisible(true);
            getChildren().filter(Span.class::isInstance).toList().forEach(this::remove);
        }
    }

    /** On each pass of the runners: the progress moves in the dots and lines on screen; the grid is not reloaded. */
    void refreshProgress() {
        progress = ProjectsModel.progress(live.snapshot(org), clock.instant(), messages);
        showProgress();
    }

    private void showProgress() {
        mainDots.forEach((repo, d) -> Badges.describe(d.dot(), d.state(), progress.main(repo), messages));
        pullProgress.forEach((pr, span) -> show(span, progress.pull(pr.repo(), pr.number())));
    }

    private static void show(Span span, String progress) {
        span.setText(progress == null ? "" : progress);
        span.setVisible(progress != null);
    }

    private Span mainDot(ProjectRow row) {
        Span dot = Badges.dot(row.main(), progress.main(row.repo()), messages);
        mainDots.put(row.repo(), new MainDot(dot, row.main()));
        return dot;
    }

    private Component pullCounter(ProjectRow row) {
        Button counter = new Button(Badges.pulls(row, messages), e -> {
            boolean open = !grid.isDetailsVisible(row);
            grid.setDetailsVisible(row, open);
            if (open) {
                unfolded.add(row.repo());
            } else {
                unfolded.remove(row.repo());
            }
        });
        counter.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        counter.setEnabled(row.pulls() > 0);
        return counter;
    }

    Component pullList(ProjectRow row) {
        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(false);
        list.getStyle().set("padding", "0 var(--lumo-space-l) var(--lumo-space-s)");
        live.snapshot(org).repos().stream().filter(r -> r.name().equals(row.repo()))
                .flatMap(r -> r.pulls().stream())
                .sorted(Comparator.comparingInt(PullView::number).reversed())
                .forEach(pr -> list.add(pullLine(row.repo(), pr)));
        return list;
    }

    /** A PR of {@code repo}, with how far along its running build is when it has an estimate. */
    private Component pullLine(String repo, PullView pr) {
        // short of room (a phone), the title shrinks rather than push the WIP pill out of sight
        Button open = Parts.title("#" + pr.number() + " " + pr.title(), () -> drawers.openPull(org, pr));
        Span author = new Span(pr.author());
        author.addClassName(Theme.MUTED);
        Span progressOf = Parts.muted("");
        PullKey key = new PullKey(repo, pr.number());
        pullProgress.put(key, progressOf);
        show(progressOf, progress.pull(repo, pr.number()));
        HorizontalLayout line = new HorizontalLayout(Badges.dot(pr.state(), messages), open);
        if (pr.draft()) {
            line.add(Badges.wip(messages));
        }
        line.add(author, progressOf);
        line.setAlignItems(FlexComponent.Alignment.CENTER);
        // the list, aligned to the start, would let the line grow with its title: no wider than the cell
        line.getStyle().set("max-width", "100%");
        return line;
    }

    /** What the grid shows: its rows and, for the unfolded lists, the PRs of each project. */
    private record Shown(List<ProjectRow> rows, List<List<PullView>> pulls) {
    }

    private record MainDot(Span dot, CiState state) {
    }

    private record PullKey(String repo, int number) {
    }
}
