package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.UI;
import io.vidocq.tools.dashboard.i18n.Messages;

/**
 * The texts in the language of the current UI, English outside a UI. A component takes them once, when it is built,
 * so that its later refreshes (pushed from another thread) keep the same language.
 */
public final class Texts {

    private Texts() {
    }

    public static Messages current() {
        UI ui = UI.getCurrent();
        return Messages.forLocale(ui == null ? null : ui.getLocale());
    }
}
