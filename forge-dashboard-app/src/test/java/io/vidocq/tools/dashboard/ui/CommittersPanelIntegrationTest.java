package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The committers panel as the main view gets it from Vauban, on an org without commits. */
class CommittersPanelIntegrationTest {

    @Test
    void orgWithoutCommitsSaysSo() {
        CommittersPanel panel = InProcessApp.bean(CommittersPanel.class).forOrg("Nobody", 30, days -> { });
        assertTrue(descendants(panel).anyMatch(c -> c instanceof Span s
                && s.getText().equals("Aucun commit sur 30 jours dans Nobody.")), "the empty state is shown");
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(CommittersPanelIntegrationTest::descendants));
    }
}
