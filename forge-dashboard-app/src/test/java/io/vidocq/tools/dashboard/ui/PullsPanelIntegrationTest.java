package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.live.RunnerView;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.store.JobExecutionRepository;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.testing.TestClock;
import io.vidocq.tools.dashboard.ui.model.PullRow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static io.vidocq.tools.dashboard.ui.ProjectsPanelIntegrationTest.descendants;
import static io.vidocq.tools.dashboard.ui.ProjectsPanelIntegrationTest.visibleHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The PR tab as the main view gets it from Vauban. */
class PullsPanelIntegrationTest {

    private static final int PULL = 2;
    private static final int UPDATED = 5;
    private static final int BUILD = 6;

    /** On a phone: the state, the project and the PR; the author, gate, last change and build come back wider. */
    @Test
    void narrowScreenKeepsStateRepoAndPull() {
        PullsPanel panel = panel("Nobody");

        panel.narrow(true);
        assertEquals(List.of("CI", "Repo", "PR"), visibleHeaders(panel));

        panel.narrow(false);
        assertEquals(List.of("CI", "Repo", "PR", "Auteur", "Gate", "Mise à jour", "Build"), visibleHeaders(panel));
    }

    @Test
    void anOrgWithoutOpenPullSaysSo() {
        PullsPanel panel = panel("Nobody");

        assertTrue(descendants(panel).anyMatch(c -> c instanceof Span s && s.getText()
                .equals("Aucune PR ouverte dans Nobody.")), "the empty state");
    }

    /** The PR cell opens the PR's drawer; a draft carries the neutral WIP pill. */
    @Test
    void thePullOpensItsDrawerAndADraftSaysWip() {
        String org = "PullsDrawer";
        vaubanWith(org, "WIP: proxies");
        PullsPanel panel = panel(org);
        Component cell = TestUi.get(Locale.FRENCH, () -> column(panel, PULL).createComponent(firstRow(panel)));
        assertTrue(descendants(cell).anyMatch(c -> c instanceof Span s && s.getText().equals("WIP")
                && s.hasClassName("dash-pill-wip")), "the WIP pill");

        Button open = descendants(cell).filter(Button.class::isInstance).map(Button.class::cast).findFirst()
                .orElseThrow();
        TestUi.run(Locale.FRENCH, open::click);

        assertTrue(descendants(panel).anyMatch(c -> c instanceof PullDetailsDrawer d && d.isVisible()),
                "the drawer of #7 is open");
    }

    /** A running build's progress moves with the runners, in place: the grid is not reloaded. */
    @Test
    void aRunningBuildsProgressMovesInPlace() {
        String org = "PullsProgress";
        vaubanWith(org, "feat: proxies");
        running(org, 30);
        PullsPanel panel = panel(org);
        var items = grid(panel).getDataProvider();
        Component progress = TestUi.get(Locale.FRENCH, () -> column(panel, BUILD).createComponent(firstRow(panel)));
        assertTrue(shows(progress, "30 % · reste ~1 h 10 min"), "the progress of #7");

        running(org, 50);
        TestUi.run(Locale.FRENCH, panel::refreshProgress);

        assertTrue(shows(progress, "50 % · reste ~50 min"), "the progress of #7, moved");
        assertSame(items, grid(panel).getDataProvider());
    }

    /** A pass of the PRs that changes nothing leaves the grid alone: the keyboard focus stays where it is. */
    @Test
    void aPassThatChangesNothingLeavesTheGridAlone() {
        String org = "PullsUnchanged";
        vaubanWith(org, "feat: proxies");
        PullsPanel panel = panel(org);
        var items = grid(panel).getDataProvider();

        TestUi.run(Locale.FRENCH, panel::refresh);

        assertSame(items, grid(panel).getDataProvider());
    }

    /** A red PR ready for review stands out in red, as a project in error does; a red draft does not. */
    @Test
    void redReadyPullsGetTheErrorPart() {
        Grid<PullRow> grid = grid(panel("Nobody"));
        PullView red = new PullView("vauban", 7, "feat: x", "y", "sha", CiState.FAILURE, List.of(), GateStatus.OK, "u",
                Instant.now());
        PullView redDraft = new PullView("vauban", 8, "WIP: x", "y", "sha", CiState.FAILURE, List.of(), GateStatus.OK,
                "u", Instant.now());

        assertEquals("error", grid.getPartNameGenerator().apply(new PullRow(red, "—")));
        assertNull(grid.getPartNameGenerator().apply(new PullRow(redDraft, "—")));
    }

    /** Time passes, nothing else changes: the age of the last change moves in place, the grid stays. */
    @Test
    void theAgeOfTheLastChangeMovesInPlace() {
        String org = "PullsAge";
        TestClock clock = new TestClock(Instant.parse("2026-11-16T10:03:00Z"));
        vaubanWith(org, "feat: proxies");
        PullsPanel panel = TestUi.get(Locale.FRENCH, () -> new PullsPanel(InProcessApp.bean(LiveState.class),
                InProcessApp.bean(ChangeBus.class), InProcessApp.bean(JobExecutionRepository.class),
                InProcessApp.bean(RunnerRowRepository.class), clock).forOrg(org));
        var items = grid(panel).getDataProvider();
        Component age = TestUi.get(Locale.FRENCH, () -> column(panel, UPDATED).createComponent(firstRow(panel)));
        assertTrue(shows(age, "il y a 3 min"), "3 min after #7 changed");

        clock.advance(Duration.ofMinutes(2));
        TestUi.run(Locale.FRENCH, panel::refresh);

        assertTrue(shows(age, "il y a 5 min"), "2 min later");
        assertSame(items, grid(panel).getDataProvider());
    }

    private static PullsPanel panel(String org) {
        return TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(PullsPanel.class).forOrg(org));
    }

    @SuppressWarnings("unchecked")
    private static Grid<PullRow> grid(Component root) {
        return descendants(root).filter(Grid.class::isInstance).map(c -> (Grid<PullRow>) c).findFirst()
                .orElseThrow();
    }

    private static PullRow firstRow(PullsPanel panel) {
        return grid(panel).getGenericDataView().getItems().findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static ComponentRenderer<? extends Component, PullRow> column(PullsPanel panel, int index) {
        return (ComponentRenderer<? extends Component, PullRow>) grid(panel).getColumns().get(index).getRenderer();
    }

    private static boolean shows(Component root, String text) {
        return descendants(root).anyMatch(c -> c instanceof Span s && s.isVisible() && s.getText().equals(text));
    }

    /** vauban with its PR #7, titled {@code title}, running. */
    private static void vaubanWith(String org, String title) {
        Instant now = Instant.now();
        PullView pr = new PullView("vauban", 7, title, "yblazart", "sha", CiState.PENDING, List.of(), GateStatus.OK,
                "u", Instant.parse("2026-11-16T10:00:00Z"));
        RepoView vauban = new RepoView("vauban", CiState.SUCCESS, "abc", "fix: y", now.minus(Duration.ofHours(2)),
                null, List.of(pr), "u");
        InProcessApp.bean(LiveState.class).update(org, s -> s.withRepos(List.of(vauban), now),
                new ChangeEvent.PullsChanged(org));
    }

    /** A job of vauban's PR #7 running for {@code minutes} out of its usual 100: a percent point a minute. */
    private static void running(String org, long minutes) {
        Instant now = Instant.now();
        JobView job = new JobView(1, 1001, "vauban", 7, null, "build", "running",
                now.minus(Duration.ofMinutes(minutes)), "u", 1219L, "r1", Duration.ofMinutes(100));
        RunnerView runner = new RunnerView(1219, "r1", "active", List.of(), 2, List.of(job));
        InProcessApp.bean(LiveState.class).update(org, s -> s.withRunners(List.of(runner), List.of(), now),
                new ChangeEvent.RunnersChanged(org));
    }
}
