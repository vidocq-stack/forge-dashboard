package io.vidocq.tools.dashboard.ui;

/** The three views of an org, toggled in the header; each one's label is the text of {@link #key()}. */
public enum ViewMode {
    PROJECTS, PULLS, CI, COMMITTERS;

    public String key() {
        return "view." + name();
    }
}
