package io.vidocq.tools.dashboard.ui;

import java.util.List;

/**
 * The dashboard's look: its stylesheet, loaded after Lumo by the shell, and the CSS classes and grid parts the views
 * put on their components. Every one of them has a rule in the stylesheet ({@code ThemeTest} checks it).
 */
final class Theme {

    /** Relative to the application's {@code META-INF/resources/}. */
    static final String STYLESHEET = "styles/dashboard.css";

    /** A small rounded label: a Sonar gate, a count of pull requests. */
    static final String PILL = "dash-pill";
    static final String PILL_OK = "dash-pill-ok";
    static final String PILL_ERR = "dash-pill-err";
    static final String PILL_WARN = "dash-pill-warn";
    static final String PILL_PENDING = "dash-pill-pending";
    static final String PILL_ZERO = "dash-pill-zero";
    /** A draft, neutral: neither green nor red. */
    static final String PILL_WIP = "dash-pill-wip";
    /** The row of pills of the PR column. */
    static final String PILLS = "dash-pills";
    /** Secondary text. */
    static final String MUTED = "dash-muted";
    /** The main view's header, ruled from the page. */
    static final String HEADER = "dash-header";
    /** A framed group of buttons, the selected one filled: views, occupancy windows, periods. */
    static final String TOGGLE = "dash-toggle";
    /** A framed box: a runner. */
    static final String CARD = "dash-card";

    /** The CSS classes the views use. */
    static final List<String> CLASSES = List.of(PILL, PILL_OK, PILL_ERR, PILL_WARN, PILL_PENDING, PILL_ZERO, PILL_WIP,
            PILLS, MUTED, HEADER, TOGGLE, CARD);

    /** Cells of a projects grid row in error (red main, red pull request, failed gate). */
    static final String ERROR_ROW = "error";

    /** The grid parts the views set. */
    static final List<String> PARTS = List.of(ERROR_ROW);

    private Theme() {
    }
}
