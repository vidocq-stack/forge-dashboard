package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CI panel as the main view gets it from Vauban, on an org the collector has not filled yet. */
class CiPanelIntegrationTest {

    @Test
    void emptyOrgSaysTheCollectionIsComing() {
        CiPanel panel = InProcessApp.bean(CiPanel.class).forOrg("Nobody");
        assertTrue(descendants(panel).anyMatch(c -> c instanceof Span s && s.getText().contains("Aucun runner")),
                "the empty state is shown");
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(CiPanelIntegrationTest::descendants));
    }
}
