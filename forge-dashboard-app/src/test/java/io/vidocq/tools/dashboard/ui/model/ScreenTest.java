package io.vidocq.tools.dashboard.ui.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenTest {

    @Test
    void phoneWidthsAreNarrow() {
        assertTrue(Screen.narrow(390));
        assertTrue(Screen.narrow(719));
    }

    /** A phone held sideways (844 px), a tablet or a desktop gets the full tables. */
    @Test
    void widerScreensAreNot() {
        assertFalse(Screen.narrow(720));
        assertFalse(Screen.narrow(844));
        assertFalse(Screen.narrow(1440));
    }

    /** Vaadin reports -1 (or 0) when the browser did not send its width: the full layout, as before. */
    @Test
    void unknownWidthIsNotNarrow() {
        assertFalse(Screen.narrow(-1));
        assertFalse(Screen.narrow(0));
    }
}
