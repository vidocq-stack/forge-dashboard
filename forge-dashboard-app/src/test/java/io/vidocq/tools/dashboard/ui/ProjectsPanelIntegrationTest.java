package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The projects panel as the main view gets it from Vauban. */
class ProjectsPanelIntegrationTest {

    /** On a phone the table keeps what is read at a glance; LOC, tests, coverage and last commit come back wider. */
    @Test
    void narrowScreenKeepsRepoMainGateAndPullRequests() {
        ProjectsPanel panel = InProcessApp.bean(ProjectsPanel.class).forOrg("Nobody");

        panel.narrow(true);
        assertEquals(List.of("Repo", "main", "Gate", "PR"), visibleHeaders(panel));

        panel.narrow(false);
        assertEquals(List.of("Repo", "main", "LOC", "Tests", "Couv.", "Gate", "PR", "Dernier commit"),
                visibleHeaders(panel));
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
