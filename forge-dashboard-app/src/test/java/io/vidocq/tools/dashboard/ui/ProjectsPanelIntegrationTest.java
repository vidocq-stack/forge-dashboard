package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.live.RunnerView;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.ui.model.ProjectRow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The projects panel as the main view gets it from Vauban. */
class ProjectsPanelIntegrationTest {

    /** On a phone the table keeps what is read at a glance; LOC, tests, coverage and last commit come back wider. */
    @Test
    void narrowScreenKeepsRepoMainGateAndPullRequests() {
        ProjectsPanel panel = TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(ProjectsPanel.class).forOrg("Nobody"));

        panel.narrow(true);
        assertEquals(List.of("Repo", "main", "Gate", "PR"), visibleHeaders(panel));

        panel.narrow(false);
        assertEquals(List.of("Repo", "main", "LOC", "Tests", "Couv.", "Gate", "PR", "Dernier commit"),
                visibleHeaders(panel));
    }

    @Test
    void headersFollowTheUiLanguage() {
        ProjectsPanel panel = TestUi.get(Locale.GERMAN, () -> InProcessApp.bean(ProjectsPanel.class).forOrg("Nobody"));

        assertEquals(List.of("Repo", "main", "LOC", "Tests", "Abd.", "Gate", "PR", "Letzter Commit"),
                visibleHeaders(panel));
    }

    /** Outside a UI (a test, a background thread): English. */
    @Test
    void englishOutsideAUi() {
        ProjectsPanel panel = InProcessApp.bean(ProjectsPanel.class).forOrg("Nobody");

        assertEquals(List.of("Repo", "main", "LOC", "Tests", "Cov.", "Gate", "PR", "Last commit"),
                visibleHeaders(panel));
    }

    /**
     * A long project name ("vidocq-workspace") does not push the PR pills past a 390 px phone: the Repo column takes
     * the room left, and a name too long for it ends in an ellipsis.
     */
    @Test
    void theRepoColumnTakesTheRoomLeft() {
        Grid.Column<ProjectRow> repo = grid(panel("Nobody")).getColumns().getFirst();
        assertFalse(repo.isAutoWidth(), "an auto width would be the longest name's");
        assertEquals(1, repo.getFlexGrow());
        // an em is each cell's, and the header's font is smaller than the rows': the header would not line up
        assertTrue(repo.getWidth().endsWith("rem"), repo.getWidth());
    }

    /** A project in error gets the grid part the stylesheet tints red; the others get none. */
    @Test
    void errorRowsGetTheErrorPart() {
        ProjectsPanel panel = TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(ProjectsPanel.class).forOrg("Nobody"));
        Grid<ProjectRow> grid = grid(panel);
        ProjectRow failing = new ProjectRow("cyrano", CiState.FAILURE, "—", "—", "—", GateStatus.NONE, 0, 0, 0, 0,
                "—", "u", true);
        ProjectRow healthy = new ProjectRow("vauban", CiState.SUCCESS, "—", "—", "—", GateStatus.OK, 0, 0, 0, 0,
                "—", "u", false);

        assertEquals("error", grid.getPartNameGenerator().apply(failing));
        assertNull(grid.getPartNameGenerator().apply(healthy));
    }

    /** The rows change with the PRs and Sonar; the runners only move the progress of the running builds. */
    @Test
    void theRunnersOnlyMoveTheProgress() {
        Predicate<ChangeEvent> rows = ProjectsPanel.rowsInterest("Vidocq");
        Predicate<ChangeEvent> progress = ProjectsPanel.progressInterest("Vidocq");

        assertTrue(rows.test(new ChangeEvent.PullsChanged("Vidocq")));
        assertTrue(rows.test(new ChangeEvent.SonarChanged("Vidocq")));
        assertFalse(rows.test(new ChangeEvent.RunnersChanged("Vidocq")));
        assertTrue(progress.test(new ChangeEvent.RunnersChanged("Vidocq")));
        assertFalse(progress.test(new ChangeEvent.RunnersChanged("VidocqTools")));
    }

    /** A pass that changes nothing visible leaves the grid alone: no reload, the keyboard focus stays where it is. */
    @Test
    void aPassThatChangesNothingVisibleLeavesTheGridAlone() {
        String org = "ProjectsUnchanged";
        vaubanIn(org, CiState.SUCCESS, 7);
        ProjectsPanel panel = panel(org);
        var items = grid(panel).getDataProvider();

        TestUi.run(Locale.FRENCH, panel::refresh);

        assertSame(items, grid(panel).getDataProvider());
    }

    /** A PR replaced by another of the same state changes no count, but the unfolded list must show the new one. */
    @Test
    void aPullReplacedByAnotherReloadsTheGrid() {
        String org = "ProjectsPullReplaced";
        vaubanIn(org, CiState.SUCCESS, 7);
        ProjectsPanel panel = panel(org);
        var items = grid(panel).getDataProvider();
        vaubanIn(org, CiState.SUCCESS, 9);

        TestUi.run(Locale.FRENCH, panel::refresh);

        assertNotSame(items, grid(panel).getDataProvider());
    }

    /** The dot of a running main says how far along it is, and follows the runners without reloading the grid. */
    @Test
    void aRunningMainsDotFollowsTheRunnersInPlace() {
        String org = "ProjectsMainProgress";
        vaubanIn(org, CiState.PENDING, 7);
        running(org, null, 30);
        ProjectsPanel panel = panel(org);
        Grid<ProjectRow> grid = grid(panel);
        var items = grid.getDataProvider();
        Component dot = TestUi.get(Locale.FRENCH, () -> mainColumn(grid).createComponent(firstRow(grid)));
        assertEquals("en cours · 30 % · reste ~1 h 10 min", dot.getElement().getAttribute("title"));

        running(org, null, 50);
        TestUi.run(Locale.FRENCH, panel::refreshProgress);

        assertEquals("en cours · 50 % · reste ~50 min", dot.getElement().getAttribute("title"));
        assertSame(items, grid.getDataProvider());
    }

    /** Unfolded, a PR with a running build says how far along it is, and follows the runners in place. */
    @Test
    void anUnfoldedPullFollowsTheRunnersInPlace() {
        String org = "ProjectsPullProgress";
        vaubanIn(org, CiState.SUCCESS, 7);
        running(org, 7, 30);
        ProjectsPanel panel = panel(org);
        Component pulls = TestUi.get(Locale.FRENCH, () -> panel.pullList(firstRow(grid(panel))));
        assertTrue(shows(pulls, "30 % · reste ~1 h 10 min"), "the progress of #7");

        running(org, 7, 50);
        TestUi.run(Locale.FRENCH, panel::refreshProgress);

        assertTrue(shows(pulls, "50 % · reste ~50 min"), "the progress of #7, moved");
    }

    /** Unfolded, a tagged PR shows its component tags as pills, then its short title (the whole one in the tooltip). */
    @Test
    void anUnfoldedTaggedPullShowsItsTags() {
        String org = "ProjectsTagged";
        String title = "[WIP] [NOETHER][VAUBAN] fix(persistence): flush";
        vaubanIn(org, CiState.SUCCESS, 7, title);
        ProjectsPanel panel = panel(org);
        Component pulls = TestUi.get(Locale.FRENCH, () -> panel.pullList(firstRow(grid(panel))));
        List<Component> content = descendants(pulls).toList();

        List<Span> tags = content.stream().filter(Span.class::isInstance).map(Span.class::cast)
                .filter(s -> s.hasClassName("dash-pill-tag")).toList();
        assertEquals(List.of("NOETHER", "VAUBAN"), tags.stream().map(Span::getText).toList(), "the tag pills");

        Button open = content.stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst()
                .orElseThrow();
        assertEquals("#7 fix(persistence): flush", open.getText(), "the short title");
        assertEquals("#7 " + title, open.getElement().getAttribute("title"), "the whole title in the tooltip");
        assertTrue(content.indexOf(tags.get(1)) < content.indexOf(open), "the tags come before the title");
    }

    /** Unfolded, a monorepo PR shows the components of its changed files after its title's tags. */
    @Test
    void anUnfoldedPullShowsTheComponentsOfItsFiles() {
        String org = "ProjectsFiles";
        vaubanIn(org, CiState.SUCCESS, 7, "[VAUBAN] x", List.of("FOY"));
        ProjectsPanel panel = panel(org);
        Component pulls = TestUi.get(Locale.FRENCH, () -> panel.pullList(firstRow(grid(panel))));

        List<Span> tags = descendants(pulls).filter(Span.class::isInstance).map(Span.class::cast)
                .filter(s -> s.hasClassName("dash-pill-tag")).toList();
        assertEquals(List.of("VAUBAN", "FOY"), tags.stream().map(Span::getText).toList(), "the tag pills");
        assertEquals("Composant : foy (fichiers modifiés)", tags.get(1).getElement().getAttribute("title"));
    }

    /** Unfolded, a draft carries the neutral WIP pill; a PR ready for review does not. */
    @Test
    void anUnfoldedDraftCarriesTheWipPill() {
        String org = "ProjectsDraft";
        vaubanIn(org, CiState.SUCCESS, 7, "WIP: proxies");
        ProjectsPanel panel = panel(org);

        Component pulls = TestUi.get(Locale.FRENCH, () -> panel.pullList(firstRow(grid(panel))));

        assertTrue(descendants(pulls).anyMatch(c -> c instanceof Span s && s.getText().equals("WIP")
                && s.hasClassName("dash-pill-wip")), "the WIP pill of #7");
        // on a phone, a long title shrinks rather than push the pill out of sight
        assertTrue(descendants(pulls).anyMatch(c -> c instanceof Button b && b.getText().startsWith("#7 ")
                && b.hasClassName("dash-title")), "the title of #7 shrinks");
        // ...within its line, which the list (aligned to the start) would otherwise let grow with the title
        Component line = descendants(pulls).filter(c -> c instanceof Button b && b.getText().startsWith("#7 "))
                .findFirst().orElseThrow().getParent().orElseThrow();
        assertEquals("100%", line.getElement().getStyle().get("max-width"));
        assertEquals("wrap", line.getElement().getStyle().get("flex-wrap"), "short of room, the progress goes below");
        // only the title shrinks: the author and the progress stay on one line
        line.getChildren().filter(c -> c instanceof Span s && s.hasClassName("dash-muted")).forEach(s -> {
            assertEquals("0", s.getElement().getStyle().get("flex-shrink"), "author and progress keep their size");
            assertEquals("nowrap", s.getElement().getStyle().get("white-space"));
        });
        assertEquals(2, line.getChildren().filter(c -> c instanceof Span s && s.hasClassName("dash-muted")).count());
    }

    private static ProjectsPanel panel(String org) {
        return TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(ProjectsPanel.class).forOrg(org));
    }

    private static ProjectRow firstRow(Grid<ProjectRow> grid) {
        return grid.getGenericDataView().getItems().findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static ComponentRenderer<? extends Component, ProjectRow> mainColumn(Grid<ProjectRow> grid) {
        return (ComponentRenderer<? extends Component, ProjectRow>) grid.getColumns().get(1).getRenderer();
    }

    private static boolean shows(Component root, String text) {
        return descendants(root).anyMatch(c -> c instanceof Span s && s.isVisible() && s.getText().equals(text));
    }

    /** vauban, its main in {@code main}, with one running PR numbered {@code pull}. */
    private static void vaubanIn(String org, CiState main, int pull) {
        vaubanIn(org, main, pull, "feat: x");
    }

    private static void vaubanIn(String org, CiState main, int pull, String title) {
        vaubanIn(org, main, pull, title, List.of());
    }

    private static void vaubanIn(String org, CiState main, int pull, String title, List<String> fileTags) {
        Instant now = Instant.now();
        PullView pr = new PullView("vauban", pull, title, "yblazart", "sha", CiState.PENDING, List.of(),
                GateStatus.NONE, "u", Instant.parse("2026-11-15T10:00:00Z"), fileTags);
        RepoView vauban = new RepoView("vauban", main, "abc", "fix: y", now.minus(Duration.ofHours(2)), null,
                List.of(pr), "u");
        InProcessApp.bean(LiveState.class).update(org, s -> s.withRepos(List.of(vauban), now),
                new ChangeEvent.PullsChanged(org));
    }

    /**
     * A job of vauban's PR {@code pr} (null: of main) running for {@code minutes} out of its usual 100: a percent
     * point a minute, so a slow test does not move it.
     */
    private static void running(String org, Integer pr, long minutes) {
        Instant now = Instant.now();
        JobView job = new JobView(1, 1001, "vauban", pr, null, "build", "running",
                now.minus(Duration.ofMinutes(minutes)), "u", 1219L, "r1", Duration.ofMinutes(100));
        RunnerView runner = new RunnerView(1219, "r1", "active", List.of(), 2, List.of(job));
        InProcessApp.bean(LiveState.class).update(org, s -> s.withRunners(List.of(runner), List.of(), now),
                new ChangeEvent.RunnersChanged(org));
    }

    @SuppressWarnings("unchecked")
    private static Grid<ProjectRow> grid(Component root) {
        return descendants(root).filter(Grid.class::isInstance).map(c -> (Grid<ProjectRow>) c).findFirst()
                .orElseThrow();
    }

    static List<String> visibleHeaders(Component root) {
        Grid<?> grid = descendants(root).filter(Grid.class::isInstance).map(c -> (Grid<?>) c).findFirst()
                .orElseThrow();
        return grid.getColumns().stream().filter(Grid.Column::isVisible).map(Grid.Column::getHeaderText).toList();
    }

    static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(ProjectsPanelIntegrationTest::descendants));
    }
}
