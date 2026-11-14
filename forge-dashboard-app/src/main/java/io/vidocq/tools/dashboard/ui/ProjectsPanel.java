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
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.ui.model.ProjectRow;
import io.vidocq.tools.dashboard.ui.model.ProjectsModel;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The projects of an org: state of main, Sonar figures, open PRs counted by state, last commit. The PR counter
 * unfolds the PRs of the repo; a PR opens its drawer. Refreshed by push on the PR and Sonar changes of the org.
 */
@Dependent
public class ProjectsPanel extends Div {

    private final LiveState live;
    private final ChangeBus bus;
    private final Clock clock;
    private final Grid<ProjectRow> grid = new Grid<>();
    private final Drawers drawers;
    private final Set<String> unfolded = new HashSet<>();
    private String org;

    @Inject
    public ProjectsPanel(LiveState live, ChangeBus bus, JobExecutionRepository jobs, RunnerRowRepository runners,
            Clock clock) {
        this.live = live;
        this.bus = bus;
        this.clock = clock;
        this.drawers = new Drawers(live, bus, jobs, runners, clock);
        setWidthFull();

        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.addComponentColumn(r -> Parts.link(r.repo(), r.htmlUrl())).setHeader("Repo")
                .setAutoWidth(true).setFlexGrow(1);
        grid.addComponentColumn(r -> Badges.dot(r.main())).setHeader("main").setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER);
        grid.addColumn(ProjectRow::loc).setHeader("LOC").setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(ProjectRow::tests).setHeader("Tests").setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addColumn(ProjectRow::coverage).setHeader("Couv.").setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(r -> Badges.gate(r.gate())).setHeader("Gate").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::pullCounter).setHeader("PR").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(ProjectRow::lastCommit).setHeader("Dernier commit").setAutoWidth(true).setFlexGrow(0);
        grid.setDetailsVisibleOnClick(false);
        grid.setItemDetailsRenderer(new ComponentRenderer<>(this::pullList));
        add(grid, drawers);
    }

    /** Shows {@code org}; called once, right after creation. */
    public ProjectsPanel forOrg(String org) {
        this.org = org;
        refresh();
        LiveBinding.bind(this, bus, e -> e.equals(new ChangeEvent.PullsChanged(org))
                || e.equals(new ChangeEvent.SonarChanged(org)), this::refresh);
        return this;
    }

    private void refresh() {
        List<ProjectRow> rows = ProjectsModel.rows(live.snapshot(org), clock.instant());
        grid.setItems(rows);
        rows.stream().filter(r -> unfolded.contains(r.repo())).forEach(r -> grid.setDetailsVisible(r, true));
        if (rows.isEmpty()) {
            grid.setVisible(false);
            if (getChildren().noneMatch(Span.class::isInstance)) {
                addComponentAsFirst(new Span("Pas encore de projets pour " + org + " : la collecte arrive."));
            }
        } else {
            grid.setVisible(true);
            getChildren().filter(Span.class::isInstance).toList().forEach(this::remove);
        }
    }

    private Component pullCounter(ProjectRow row) {
        Button counter = new Button(row.pullCounts(), e -> {
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

    private Component pullList(ProjectRow row) {
        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(false);
        list.getStyle().set("padding", "0 var(--lumo-space-l) var(--lumo-space-s)");
        live.snapshot(org).repos().stream().filter(r -> r.name().equals(row.repo()))
                .flatMap(r -> r.pulls().stream())
                .sorted(Comparator.comparingInt(PullView::number).reversed())
                .forEach(pr -> list.add(pullLine(pr)));
        return list;
    }

    private Component pullLine(PullView pr) {
        Button open = Parts.action("#" + pr.number() + " " + pr.title(), () -> drawers.openPull(org, pr));
        Span author = new Span(pr.author());
        author.getStyle().set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout line = new HorizontalLayout(Badges.dot(pr.state()), open, author);
        line.setAlignItems(FlexComponent.Alignment.CENTER);
        return line;
    }
}
