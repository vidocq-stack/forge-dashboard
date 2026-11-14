package io.vidocq.tools.dashboard.ui.model;

/** Screen widths: below {@link #NARROW_BELOW} pixels (a phone held upright) the views keep only the essentials. */
public final class Screen {

    /** In CSS pixels, the browser's {@code window.innerWidth}. */
    public static final int NARROW_BELOW = 720;

    private Screen() {
    }

    /** {@code width} in CSS pixels; -1 or 0 when the browser did not send it. */
    public static boolean narrow(int width) {
        return width > 0 && width < NARROW_BELOW;
    }
}
