package io.vidocq.tools.dashboard.ui;

import java.util.List;

/**
 * The dashboard's look: its stylesheet, loaded after Lumo by the shell, and the CSS classes and grid parts the views
 * put on their components. Every one of them has a rule in the stylesheet ({@code ThemeTest} checks it).
 */
final class Theme {

    /** Relative to the application's {@code META-INF/resources/}. */
    static final String STYLESHEET = "styles/dashboard.css";

    /** The CSS classes the views use. */
    static final List<String> CLASSES = List.of();

    /** The grid parts the views set. */
    static final List<String> PARTS = List.of();

    private Theme() {
    }
}
