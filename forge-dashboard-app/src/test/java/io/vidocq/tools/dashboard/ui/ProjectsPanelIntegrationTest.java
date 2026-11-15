package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
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
import java.util.Map;
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

    /** A project in error gets the grid part the stylesheet tints red; the others get none. */
    @Test
    void errorRowsGetTheErrorPart() {
        ProjectsPanel panel = TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(ProjectsPanel.class).forOrg("Nobody"));
        Grid<ProjectRow> grid = grid(panel);
        ProjectRow failing = new ProjectRow("cyrano", CiState.FAILURE, "—", "—", "—", GateStatus.NONE, 0, 0, 0, "—",
                "u", true, null, Map.of());
        ProjectRow healthy = new ProjectRow("vauban", CiState.SUCCESS, "—", "—", "—", GateStatus.OK, 0, 0, 0, "—",
                "u", false, null, Map.of());

        assertEquals("error", grid.getPartNameGenerator().apply(failing));
        assertNull(grid.getPartNameGenerator().apply(healthy));
    }

    /** A running build's progress moves with each pass of the runners, not only with those of the PRs. */
    @Test
    void followsTheRunnersToo() {
        Predicate<ChangeEvent> interest = ProjectsPanel.interest("Vidocq");

        assertTrue(interest.test(new ChangeEvent.RunnersChanged("Vidocq")));
        assertTrue(interest.test(new ChangeEvent.PullsChanged("Vidocq")));
        assertTrue(interest.test(new ChangeEvent.SonarChanged("Vidocq")));
        assertFalse(interest.test(new ChangeEvent.RunnersChanged("VidocqTools")));
    }

    /** A pass that changes nothing visible leaves the grid alone: no reload, the keyboard focus stays where it is. */
    @Test
    void aPassThatChangesNothingVisibleLeavesTheGridAlone() {
        String org = "ProjectsUnchanged";
        withVaubanRunning(org, null);
        ProjectsPanel panel = TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(ProjectsPanel.class).forOrg(org));
        var items = grid(panel).getDataProvider();

        TestUi.run(Locale.FRENCH, panel::refresh);

        assertSame(items, grid(panel).getDataProvider());
    }

    /** A PR replaced by another of the same state changes no count, but the unfolded list must show the new one. */
    @Test
    void aPullReplacedByAnotherReloadsTheGrid() {
        String org = "ProjectsPullReplaced";
        withVaubanRunning(org, null);
        ProjectsPanel panel = TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(ProjectsPanel.class).forOrg(org));
        var items = grid(panel).getDataProvider();
        Instant now = Instant.now();
        InProcessApp.bean(LiveState.class).update(org, s -> s.withRepos(List.of(vauban(9, now)), now),
                new ChangeEvent.PullsChanged(org));

        TestUi.run(Locale.FRENCH, panel::refresh);

        assertNotSame(items, grid(panel).getDataProvider());
    }

    /** Unfolded, a PR with a running build says how far along it is. */
    @Test
    void anUnfoldedPullSaysHowFarAlongItsBuildIs() {
        String org = "ProjectsProgress";
        withVaubanRunning(org, 7);
        ProjectsPanel panel = TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(ProjectsPanel.class).forOrg(org));
        ProjectRow row = grid(panel).getGenericDataView().getItems().findFirst().orElseThrow();

        Component pulls = TestUi.get(Locale.FRENCH, () -> panel.pullList(row));

        assertTrue(descendants(pulls).anyMatch(c -> c instanceof Span s && s.getText().equals("60 % · reste ~2 min")),
                "the progress of #7");
    }

    /** vauban, with PR #7, and a job of PR {@code pr} (null: of main) running for 3 min out of its usual 5. */
    private static void withVaubanRunning(String org, Integer pr) {
        Instant now = Instant.now();
        RepoView vauban = vauban(7, now);
        JobView job = new JobView(1, 1001, "vauban", pr, null, "build", "running",
                now.minus(Duration.ofMinutes(3)), "u", 1219L, "r1", Duration.ofMinutes(5));
        RunnerView runner = new RunnerView(1219, "r1", "active", List.of(), 2, List.of(job));
        InProcessApp.bean(LiveState.class).update(org, s -> s.withRepos(List.of(vauban), now)
                .withRunners(List.of(runner), List.of(), now), new ChangeEvent.PullsChanged(org));
    }

    /** vauban, its main green, with one running PR. */
    private static RepoView vauban(int pull, Instant now) {
        PullView pr = new PullView("vauban", pull, "feat: x", "yblazart", "sha", CiState.PENDING, List.of(),
                GateStatus.NONE, "u", now);
        return new RepoView("vauban", CiState.SUCCESS, "abc", "fix: y", now.minus(Duration.ofHours(2)), null,
                List.of(pr), "u");
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
