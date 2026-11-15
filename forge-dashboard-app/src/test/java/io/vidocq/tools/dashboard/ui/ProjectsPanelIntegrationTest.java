package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.ui.model.ProjectRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                "u", true);
        ProjectRow healthy = new ProjectRow("vauban", CiState.SUCCESS, "—", "—", "—", GateStatus.OK, 0, 0, 0, "—",
                "u", false);

        assertEquals("error", grid.getPartNameGenerator().apply(failing));
        assertNull(grid.getPartNameGenerator().apply(healthy));
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
