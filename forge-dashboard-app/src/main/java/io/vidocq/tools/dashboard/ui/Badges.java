package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.live.ComponentTag;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.ui.model.ProjectRow;

import java.util.Locale;

/** The small coloured marks of the panels: a CI state dot, a Sonar gate pill, the PR count pills. */
final class Badges {

    private static final String GREEN = "var(--lumo-success-color)";
    private static final String RED = "var(--lumo-error-color)";
    private static final String ORANGE = "var(--lumo-warning-color, #e8a33d)";
    private static final String GREY = "var(--lumo-contrast-30pct)";

    private Badges() {
    }

    static Span dot(CiState state, Messages m) {
        return dot(state, null, m);
    }

    /** The dot of a build, with how far along it is in the tooltip when it runs with an estimate. */
    static Span dot(CiState state, String progress, Messages m) {
        Span dot = new Span("●");
        dot.getStyle().set("color", color(state)).set("font-size", "1.1em").set("line-height", "1");
        describe(dot, state, progress, m);
        return dot;
    }

    /**
     * The tooltip of a dot, and its name for a screen reader (not « black circle »): its state and, when given, how
     * far along the build is.
     */
    static void describe(Span dot, CiState state, String progress, Messages m) {
        String label = label(state, m);
        String text = progress == null ? label : label + " · " + progress;
        dot.getElement().setAttribute("title", text).setAttribute("role", "img").setAttribute("aria-label", text);
    }

    static String color(CiState state) {
        return switch (state == null ? CiState.NONE : state) {
            case SUCCESS -> GREEN;
            case FAILURE -> RED;
            case PENDING -> ORANGE;
            case NONE -> GREY;
        };
    }

    static String label(CiState state, Messages m) {
        return m.text("badge." + (state == null ? CiState.NONE : state).name());
    }

    /**
     * The PR column: the green, red and running counts of the PRs ready for review, then the drafts (WIP), one pill
     * each (a zero in grey), spelled out in the tooltip.
     */
    static Span pulls(ProjectRow row, Messages m) {
        Span pills = new Span(count(row.prGreen(), Theme.PILL_OK), count(row.prRed(), Theme.PILL_ERR),
                count(row.prPending(), Theme.PILL_PENDING), count(row.prWip(), Theme.PILL_WIP));
        pills.addClassName(Theme.PILLS);
        String counts = m.text("projects.pullCounts", row.prGreen(), row.prRed(), row.prPending(), row.prWip());
        // the tooltip for the eye; for a screen reader, the counter button takes its name from this image's label
        pills.getElement().setAttribute("title", counts).setAttribute("role", "img").setAttribute("aria-label", counts);
        return pills;
    }

    /** The mark of a draft in a list of PRs: a neutral WIP pill. */
    static Span wip(Messages m) {
        Span wip = new Span("WIP");
        wip.addClassNames(Theme.PILL, Theme.PILL_WIP);
        wip.getElement().setAttribute("title", m.text("pull.wip"));
        return wip;
    }

    /**
     * A component of a PR (« NOETHER »), as its tag stands in a title: a neutral pill whether it comes from the title
     * or from the changed files; the tooltip gives the component in lower case (what the « Composant » filter of the
     * PR tab lists) and where it comes from.
     */
    static Span tag(ComponentTag tag, Messages m) {
        Span pill = new Span(tag.tag());
        pill.addClassNames(Theme.PILL, Theme.PILL_TAG);
        String key = tag.inTitle() && tag.inFiles() ? "pull.component.both"
                : tag.inFiles() ? "pull.component.files" : "pull.component.title";
        pill.getElement().setAttribute("title", m.text(key, tag.tag().toLowerCase(Locale.ROOT)));
        return pill;
    }

    private static Span count(int n, String kind) {
        Span pill = new Span(String.valueOf(n));
        pill.addClassNames(Theme.PILL, n == 0 ? Theme.PILL_ZERO : kind);
        return pill;
    }

    /** A Sonar quality gate as a pill: OK green, ERROR red, WARN orange; « — » without a gate. */
    static Span gate(GateStatus gate) {
        GateStatus g = gate == null ? GateStatus.NONE : gate;
        if (g == GateStatus.NONE) {
            Span none = new Span("—");
            none.addClassName(Theme.MUTED);
            return none;
        }
        Span pill = new Span(g.name());
        pill.addClassNames(Theme.PILL, switch (g) {
            case OK -> Theme.PILL_OK;
            case ERROR -> Theme.PILL_ERR;
            default -> Theme.PILL_WARN;
        });
        return pill;
    }
}
