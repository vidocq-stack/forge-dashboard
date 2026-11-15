package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CI panel as the main view gets it from Vauban, on an org the collector has not filled yet. */
class CiPanelIntegrationTest {

    @Test
    void emptyOrgSaysTheCollectionIsComing() {
        CiPanel panel = TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(CiPanel.class).forOrg("Nobody"));
        assertTrue(descendants(panel).anyMatch(c -> c instanceof Span s && s.getText().contains("Aucun runner")),
                "the empty state is shown");
    }

    /** 1 h, 24 h, 7 j: a framed group of their own, their label outside the frame. */
    @Test
    void windowButtonsAreAToggle() {
        CiPanel panel = TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(CiPanel.class).forOrg("Nobody"));
        Messages fr = Messages.forLocale(Locale.FRENCH);
        List<String> labels = List.of(fr.text("duration.hours", 1L), fr.text("duration.hours", 24L),
                fr.text("duration.days", 7L));
        List<Component> parents = descendants(panel)
                .filter(c -> c instanceof Button b && labels.contains(b.getText()))
                .map(b -> b.getParent().orElseThrow()).distinct().toList();
        assertEquals(1, parents.size(), "one group for the three buttons");
        Component group = parents.getFirst();
        assertTrue(group.getElement().getClassList().contains("dash-toggle"));
        assertEquals(3, group.getChildren().count(), "only the buttons in the frame");
        // on a phone the Polish label and the frame do not fit one row: the frame goes under its label
        assertEquals("wrap", group.getParent().orElseThrow().getElement().getStyle().get("flex-wrap"));
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(CiPanelIntegrationTest::descendants));
    }
}
