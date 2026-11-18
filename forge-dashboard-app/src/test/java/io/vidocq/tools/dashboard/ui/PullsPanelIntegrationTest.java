package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(filter(panel).isVisible(), "no component to choose, no filter");
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

    /**
     * A tagged PR shows its component tags as neutral pills, then « #n short title » — the whole title in the
     * tooltip — then the WIP pill of a draft. A PR without a tag has no tag pill.
     */
    @Test
    void aTaggedPullShowsItsTagsAndItsShortTitle() {
        String org = "PullsTagged";
        String title = "[WIP] [NOETHER][VAUBAN] fix(persistence): flush";
        vaubanWith(org, title);
        PullsPanel panel = panel(org);
        Component cell = TestUi.get(Locale.FRENCH, () -> column(panel, PULL).createComponent(firstRow(panel)));
        List<Component> content = descendants(cell).toList();

        List<Span> tags = content.stream().filter(Span.class::isInstance).map(Span.class::cast)
                .filter(s -> s.hasClassName("dash-pill-tag")).toList();
        assertEquals(List.of("NOETHER", "VAUBAN"), tags.stream().map(Span::getText).toList(), "the tag pills");

        Button open = content.stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst()
                .orElseThrow();
        assertEquals("#7 fix(persistence): flush", open.getText(), "the short title");
        assertEquals("#7 " + title, open.getElement().getAttribute("title"), "the whole title in the tooltip");
        assertTrue(content.indexOf(tags.get(0)) < content.indexOf(open), "the tags come before the title");

        Span wip = content.stream().filter(Span.class::isInstance).map(Span.class::cast)
                .filter(s -> s.hasClassName("dash-pill-wip")).findFirst().orElseThrow();
        assertEquals("WIP", wip.getText());
        assertTrue(content.indexOf(open) < content.indexOf(wip), "the WIP pill comes after the title");
        assertEquals("wrap", cell.getElement().getStyle().get("flex-wrap"), "several pills wrap when room runs out");

        String plain = "PullsUntagged";
        vaubanWith(plain, "feat: proxies");
        PullsPanel plainPanel = panel(plain);
        Component plainCell = TestUi.get(Locale.FRENCH,
                () -> column(plainPanel, PULL).createComponent(firstRow(plainPanel)));
        assertTrue(descendants(plainCell).noneMatch(c -> c instanceof Span s && s.hasClassName("dash-pill-tag")),
                "no tag, no pill");
    }

    /**
     * A long title does not widen the grid past a phone's screen: the PR column takes the room left, its title shrinks
     * to an ellipsis (whole in its tooltip), and the WIP pill keeps its size.
     */
    @Test
    void aLongTitleShrinksAndTheWipPillStays() {
        String org = "PullsLongTitle";
        String title = "WIP : build(mcp): langchain4j-cdi 1.5.0, and the MCP extension ships with the release";
        vaubanWith(org, title);
        PullsPanel panel = panel(org);
        Grid.Column<PullRow> column = grid(panel).getColumns().get(PULL);
        assertFalse(column.isAutoWidth(), "an auto width would be the longest title's");
        assertEquals(1, column.getFlexGrow());

        Component cell = TestUi.get(Locale.FRENCH, () -> column(panel, PULL).createComponent(firstRow(panel)));
        Button open = descendants(cell).filter(Button.class::isInstance).map(Button.class::cast).findFirst()
                .orElseThrow();
        assertTrue(open.hasClassName("dash-title"), "the title may shrink");
        assertEquals("#7 " + title, open.getElement().getAttribute("title"));
        assertTrue(descendants(cell).anyMatch(c -> c instanceof Span s && "WIP".equals(s.getText())
                && s.hasClassName("dash-pill")), "a pill, which keeps its size (ThemeTest)");
    }

    /**
     * The last change and the build's progress move in place, which does not measure the columns again: they get a
     * width of their own, the longest progress's ("10 min langer dan gewoonlijk"), or a build started after the first
     * rendering would be cut. In rem: an em is each cell's, and the header's font is smaller than the rows'.
     */
    @Test
    void theCellsThatMoveInPlaceHaveAWidthOfTheirOwn() {
        Grid<PullRow> grid = grid(panel("Nobody"));
        for (int index : List.of(UPDATED, BUILD)) {
            Grid.Column<PullRow> column = grid.getColumns().get(index);
            assertFalse(column.isAutoWidth(), column.getHeaderText());
            assertTrue(column.getWidth() != null && column.getWidth().endsWith("rem"), column.getHeaderText());
        }
        assertTrue(grid.getColumns().get(PULL).getWidth().endsWith("rem"), "the PR column's least width");
    }

    /** A reload measures the auto-width columns again: a project with a longer name would be cut. */
    @Test
    void aReloadMeasuresTheColumnsAgain() {
        String org = "PullsRemeasure";
        vaubanWith(org, "feat: proxies");
        TestUi.run(Locale.FRENCH, () -> {
            PullsPanel panel = InProcessApp.bean(PullsPanel.class).forOrg(org);
            UI.getCurrent().add(panel);
            jsCalls();

            vaubanWith(org, "feat: proxies, renamed");
            panel.refresh();

            assertTrue(jsCalls().stream().anyMatch(js -> js.contains("recalculateColumnWidths")), "measured again");
        });
    }

    /** The JavaScript calls queued for the browser since the last call, emptied. */
    private static List<String> jsCalls() {
        UI ui = UI.getCurrent();
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        return ui.getInternals().dumpPendingJavaScriptInvocations().stream()
                .map(i -> i.getInvocation().getExpression()).toList();
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

    /**
     * The component filter above the grid lists the components of the open PRs, sorted; choosing one keeps only its
     * PRs, clearing it brings all of them back.
     */
    @Test
    void theComponentFilterKeepsItsPulls() {
        String org = "PullsFilter";
        pullsIn(org,
                repo("vauban", pr("vauban", 7, "feat: proxies")),
                repo("vidocq", pr("vidocq", 3, "[VAUBAN][CASSINI] x")),
                repo("foy", pr("foy", 1, "fix: origin")));
        PullsPanel panel = panel(org);
        ComboBox<String> filter = filter(panel);

        assertEquals("Composant", filter.getLabel());
        assertEquals("Tous les composants", filter.getPlaceholder());
        assertTrue(filter.isClearButtonVisible(), "a clear button");
        assertEquals(List.of("cassini", "foy", "vauban"), filter.getListDataView().getItems().toList(),
                "the choices of the filter");
        assertEquals(List.of("foy#1", "vauban#7", "vidocq#3"), shownPulls(panel));

        TestUi.run(Locale.FRENCH, () -> filter.setValue("vauban"));
        assertEquals(List.of("vauban#7", "vidocq#3"), shownPulls(panel), "only the PRs of vauban");

        TestUi.run(Locale.FRENCH, () -> filter.setValue(null));
        assertEquals(List.of("foy#1", "vauban#7", "vidocq#3"), shownPulls(panel), "cleared: all the PRs");
    }

    /** A component whose last open PR closes is not a choice anymore: the filter clears itself, all the PRs show. */
    @Test
    void aComponentWithoutOpenPullClearsTheFilter() {
        String org = "PullsFilterGone";
        pullsIn(org,
                repo("cassini", pr("cassini", 2, "[CASSINI] only")),
                repo("vauban", pr("vauban", 7, "feat: proxies")));
        PullsPanel panel = panel(org);
        ComboBox<String> filter = filter(panel);
        TestUi.run(Locale.FRENCH, () -> filter.setValue("cassini"));
        assertEquals(List.of("cassini#2"), shownPulls(panel));

        pullsIn(org, repo("vauban", pr("vauban", 7, "feat: proxies")));
        TestUi.run(Locale.FRENCH, panel::refresh);

        assertNull(filter.getValue(), "the filter cleared itself");
        assertEquals(List.of("vauban#7"), shownPulls(panel), "all the PRs are back");
    }

    /** A PR of a new component changes the choices of the filter: the chosen component stays chosen. */
    @Test
    void aNewComponentKeepsTheChosenFilter() {
        String org = "PullsFilterNew";
        pullsIn(org, repo("vauban", pr("vauban", 7, "feat: proxies")), repo("foy", pr("foy", 1, "fix: origin")));
        PullsPanel panel = panel(org);
        ComboBox<String> filter = filter(panel);
        TestUi.run(Locale.FRENCH, () -> filter.setValue("vauban"));

        pullsIn(org, repo("vauban", pr("vauban", 7, "feat: proxies")), repo("foy", pr("foy", 1, "fix: origin")),
                repo("vidocq", pr("vidocq", 2, "[CASSINI] x")));
        TestUi.run(Locale.FRENCH, panel::refresh);

        assertEquals(List.of("cassini", "foy", "vauban"), filter.getListDataView().getItems().toList());
        assertEquals("vauban", filter.getValue(), "still chosen");
        assertEquals(List.of("vauban#7"), shownPulls(panel));
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

    /** The org with {@code repos} and their PRs, as the pulls loop just collected them. */
    private static void pullsIn(String org, RepoView... repos) {
        Instant now = Instant.now();
        InProcessApp.bean(LiveState.class).update(org, s -> s.withRepos(List.of(repos), now),
                new ChangeEvent.PullsChanged(org));
    }

    private static RepoView repo(String name, PullView... pulls) {
        Instant now = Instant.now();
        return new RepoView(name, CiState.SUCCESS, "abc", "fix: y", now.minus(Duration.ofHours(2)), null,
                List.of(pulls), "u");
    }

    private static PullView pr(String repo, int number, String title) {
        return new PullView(repo, number, title, "yblazart", "sha" + number, CiState.PENDING, List.of(), GateStatus.OK,
                "u", Instant.parse("2026-11-16T10:00:00Z"));
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> filter(PullsPanel panel) {
        return descendants(panel).filter(ComboBox.class::isInstance).map(c -> (ComboBox<String>) c).findFirst()
                .orElseThrow();
    }

    /** The PRs of the grid, in their order. */
    private static List<String> shownPulls(PullsPanel panel) {
        return grid(panel).getGenericDataView().getItems().map(r -> r.pull().repo() + "#" + r.pull().number())
                .toList();
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
