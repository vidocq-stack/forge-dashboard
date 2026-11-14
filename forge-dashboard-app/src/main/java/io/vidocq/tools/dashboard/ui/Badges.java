package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.GateStatus;

/** The small coloured marks of the panels: a CI state dot, a Sonar gate label. */
final class Badges {

    private static final String GREEN = "var(--lumo-success-color)";
    private static final String RED = "var(--lumo-error-color)";
    private static final String ORANGE = "var(--lumo-warning-color, #e8a33d)";
    private static final String GREY = "var(--lumo-contrast-30pct)";

    private Badges() {
    }

    static Span dot(CiState state) {
        Span dot = new Span("●");
        dot.getStyle().set("color", color(state)).set("font-size", "1.1em").set("line-height", "1");
        dot.getElement().setAttribute("title", label(state));
        return dot;
    }

    static String color(CiState state) {
        return switch (state == null ? CiState.NONE : state) {
            case SUCCESS -> GREEN;
            case FAILURE -> RED;
            case PENDING -> ORANGE;
            case NONE -> GREY;
        };
    }

    static String label(CiState state) {
        return switch (state == null ? CiState.NONE : state) {
            case SUCCESS -> "vert";
            case FAILURE -> "rouge";
            case PENDING -> "en cours";
            case NONE -> "aucun statut";
        };
    }

    static Span gate(GateStatus gate) {
        GateStatus g = gate == null ? GateStatus.NONE : gate;
        Span span = new Span(g == GateStatus.NONE ? "—" : g.name());
        span.getStyle().set("font-weight", g == GateStatus.NONE ? "normal" : "600").set("color", switch (g) {
            case OK -> GREEN;
            case ERROR -> RED;
            case WARN -> ORANGE;
            case NONE -> "var(--lumo-secondary-text-color)";
        });
        return span;
    }
}
