package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.RunnerView;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
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

    /** A runner's card says how far along each running job is, after its elapsed time (1 % a minute: no flakiness). */
    @Test
    void runnerCardsSayHowFarAlongTheJobsAre() {
        String org = "CiProgress";
        Instant now = Instant.now();
        JobView job = new JobView(1, 1001, "vauban", null, "refs/heads/main", "build", "running",
                now.minus(Duration.ofMinutes(30)), "u", 1219L, "r1", Duration.ofMinutes(100));
        InProcessApp.bean(LiveState.class).update(org, s -> s.withRunners(
                List.of(new RunnerView(1219, "r1", "active", List.of(), 2, List.of(job))), List.of(), now),
                new ChangeEvent.RunnersChanged(org));

        CiPanel panel = TestUi.get(Locale.FRENCH, () -> InProcessApp.bean(CiPanel.class).forOrg(org));

        assertTrue(descendants(panel).anyMatch(c -> c instanceof Span s
                        && s.getText().matches("build · 30 min \\d\\d s · 30 % · reste ~1 h 10 min")),
                "the card line of the job");
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(CiPanelIntegrationTest::descendants));
    }
}
