package io.vidocq.tools.dashboard.ui;

/** The three views of an org, toggled in the header. */
public enum ViewMode {
    PROJECTS("Projets"), CI("CI"), COMMITTERS("Commiters");

    private final String label;

    ViewMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
