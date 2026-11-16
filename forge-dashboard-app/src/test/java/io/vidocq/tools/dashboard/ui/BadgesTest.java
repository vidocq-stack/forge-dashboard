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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgesTest {

    private static final Messages FR = Messages.forLocale(Locale.FRENCH);

    private static ProjectRow row(int green, int red, int pending) {
        return row(green, red, pending, 0);
    }

    private static ProjectRow row(int green, int red, int pending, int wip) {
        return new ProjectRow("vauban", CiState.SUCCESS, "—", "—", "—", GateStatus.OK, green, red, pending, wip, "—",
                "u", false);
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
    void pullsAreFourPills() {
        Span pills = Badges.pulls(row(1, 1, 0, 2), FR);

        assertTrue(pills.hasClassName("dash-pills"));
        List<Span> each = pills(pills);
        assertEquals(4, each.size());
        assertPill(each.get(0), "1", "dash-pill-ok");
        assertPill(each.get(1), "1", "dash-pill-err");
        assertPill(each.get(2), "0", "dash-pill-zero");
        assertPill(each.get(3), "2", "dash-pill-wip");
        assertEquals("1 verte, 1 rouge, 0 en cours, 2 WIP", pills.getElement().getAttribute("title"));
        assertPill(pills(Badges.pulls(row(0, 0, 2), FR)).get(2), "2", "dash-pill-pending");
        assertPill(pills(Badges.pulls(row(0, 0, 2), FR)).get(3), "0", "dash-pill-zero");
    }

    /** In a list of PRs, a draft carries a neutral WIP pill. */
    @Test
    void wipIsANeutralPill() {
        Span wip = Badges.wip(FR);
        assertPill(wip, "WIP", "dash-pill-wip");
        assertEquals("Brouillon : ne compte ni en vert ni en rouge", wip.getElement().getAttribute("title"));
    }

    /** A screen reader names the counter button by its content: the pills read as the counts, not « 1 1 0 ». */
    @Test
    void pullsReadAsTheirCounts() {
        Span pills = Badges.pulls(row(1, 1, 0), FR);
        assertEquals("img", pills.getElement().getAttribute("role"));
        assertEquals("1 verte, 1 rouge, 0 en cours, 0 WIP", pills.getElement().getAttribute("aria-label"));
    }

    /** « 1 verte », « 2 vertes »: the counts agree in number where the language says so. */
    @Test
    void pullCountsAgreeInNumber() {
        assertEquals("2 vertes, 3 rouges, 0 en cours, 0 WIP", title(row(2, 3, 0), FR));
        assertEquals("0 verte, 1 rouge, 4 en cours, 1 WIP", title(row(0, 1, 4, 1), FR));
        assertEquals("1 verde, 1 roja, 0 en curso, 0 WIP", title(row(1, 1, 0), Messages.forLocale(Locale.of("es"))));
        assertEquals("0 verdes, 2 rojas, 0 en curso, 0 WIP", title(row(0, 2, 0), Messages.forLocale(Locale.of("es"))));
        assertEquals("1 verde, 1 rossa, 0 in corso, 0 WIP", title(row(1, 1, 0), Messages.forLocale(Locale.ITALIAN)));
        assertEquals("1 green, 1 red, 0 running, 3 WIP", title(row(1, 1, 0, 3), Messages.forLocale(Locale.ENGLISH)));
    }

    private static String title(ProjectRow row, Messages m) {
        return Badges.pulls(row, m).getElement().getAttribute("title");
    }

    /** A project without PR: four grey zeros, still pills (its counter button is disabled). */
    @Test
    void zeroPullsAreFourZeroPills() {
        List<Span> zeros = pills(Badges.pulls(row(0, 0, 0), FR));
        assertEquals(4, zeros.size());
        zeros.forEach(p -> assertPill(p, "0", "dash-pill-zero"));
    }
}
