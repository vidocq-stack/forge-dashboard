package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.ui.model.ProjectRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgesTest {

    private static final Messages FR = Messages.forLocale(Locale.FRENCH);

    private static ProjectRow row(int green, int red, int pending) {
        return new ProjectRow("vauban", CiState.SUCCESS, "—", "—", "—", GateStatus.OK, green, red, pending, "—", "u",
                false, null, Map.of());
    }

    private static List<Span> pills(Span pills) {
        return pills.getChildren().map(Span.class::cast).toList();
    }

    private static void assertPill(Span pill, String text, String kind) {
        assertEquals(text, pill.getText());
        assertTrue(pill.hasClassName("dash-pill"), pill.getClassNames() + " lacks dash-pill");
        assertTrue(pill.hasClassName(kind), pill.getClassNames() + " lacks " + kind);
    }

    /** A running build's dot says how far along it is in its tooltip. */
    @Test
    void dotTooltipSaysHowFarAlong() {
        assertEquals("en cours · 60 % · reste ~2 min",
                Badges.dot(CiState.PENDING, "60 % · reste ~2 min", FR).getElement().getAttribute("title"));
        assertEquals("en cours", Badges.dot(CiState.PENDING, null, FR).getElement().getAttribute("title"));
    }

    @Test
    void gateIsAPill() {
        assertPill(Badges.gate(GateStatus.OK), "OK", "dash-pill-ok");
        assertPill(Badges.gate(GateStatus.ERROR), "ERROR", "dash-pill-err");
        assertPill(Badges.gate(GateStatus.WARN), "WARN", "dash-pill-warn");
        for (GateStatus none : Arrays.asList(GateStatus.NONE, null)) {
            Span dash = Badges.gate(none);
            assertEquals("—", dash.getText());
            assertTrue(dash.hasClassName("dash-muted"));
            assertFalse(dash.hasClassName("dash-pill"), "no gate, no pill");
        }
    }

    /** Green, red, running: one pill each, a zero in grey, and the counts spelled out in the tooltip. */
    @Test
    void pullsAreThreePills() {
        Span pills = Badges.pulls(row(1, 1, 0), FR);

        assertTrue(pills.hasClassName("dash-pills"));
        List<Span> each = pills(pills);
        assertEquals(3, each.size());
        assertPill(each.get(0), "1", "dash-pill-ok");
        assertPill(each.get(1), "1", "dash-pill-err");
        assertPill(each.get(2), "0", "dash-pill-zero");
        assertEquals("1 vertes, 1 rouges, 0 en cours", pills.getElement().getAttribute("title"));
        assertPill(pills(Badges.pulls(row(0, 0, 2), FR)).get(2), "2", "dash-pill-pending");
    }

    /** A screen reader names the counter button by its content: the pills read as the counts, not « 1 1 0 ». */
    @Test
    void pullsReadAsTheirCounts() {
        Span pills = Badges.pulls(row(1, 1, 0), FR);
        assertEquals("img", pills.getElement().getAttribute("role"));
        assertEquals("1 vertes, 1 rouges, 0 en cours", pills.getElement().getAttribute("aria-label"));
    }

    /** A project without PR: three grey zeros, still pills (its counter button is disabled). */
    @Test
    void zeroPullsAreThreeZeroPills() {
        List<Span> zeros = pills(Badges.pulls(row(0, 0, 0), FR));
        assertEquals(3, zeros.size());
        zeros.forEach(p -> assertPill(p, "0", "dash-pill-zero"));
    }
}
