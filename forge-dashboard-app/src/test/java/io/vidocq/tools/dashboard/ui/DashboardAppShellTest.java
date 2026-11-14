package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.lumo.Lumo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardAppShellTest {

    /**
     * Vaadin 25 applies no theme by default: without Lumo the views lose every {@code --lumo-*} colour and font. The
     * shell loads it with {@code @StyleSheet}, which keeps the precompiled production bundle (no Vite rebuild).
     */
    @Test
    void shellLoadsTheLumoStylesheet() {
        List<String> sheets = Arrays.stream(DashboardAppShell.class.getAnnotationsByType(StyleSheet.class))
                .map(StyleSheet::value).toList();

        assertTrue(sheets.contains(Lumo.STYLESHEET), "style sheets: " + sheets);
    }

    /** Server push over one WebSocket, which Foy serves since vidocq#65, instead of a long poll every few seconds. */
    @Test
    void pushGoesOverAWebSocket() {
        Push push = DashboardAppShell.class.getAnnotation(Push.class);

        assertEquals(PushMode.AUTOMATIC, push.value());
        assertEquals(Transport.WEBSOCKET_XHR, push.transport());
    }
}
