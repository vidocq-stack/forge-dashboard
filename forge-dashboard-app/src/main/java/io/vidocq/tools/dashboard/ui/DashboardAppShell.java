package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Application shell, with the Lumo theme (Vaadin 25 applies none by default, and the views use Lumo's variables) then
 * the dashboard's own stylesheet ({@link Theme}), and
 * server push over a WebSocket (Foy's Jakarta WebSocket, which accepts the handshake from the server's own origin).
 */
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Theme.STYLESHEET)
@Push(value = PushMode.AUTOMATIC, transport = Transport.WEBSOCKET_XHR)
public class DashboardAppShell implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.setPageTitle("forge-dashboard");
    }
}
