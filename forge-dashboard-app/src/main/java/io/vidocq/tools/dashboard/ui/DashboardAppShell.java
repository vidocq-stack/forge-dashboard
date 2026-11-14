package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Application shell, with the Lumo theme (Vaadin 25 applies none by default, and the views use Lumo's variables) and
 * server push.
 * <p>
 * WORKAROUND vidocq#65: long polling until Foy implements Jakarta WebSocket. With {@code WEBSOCKET_XHR} the client
 * does not fall back on its own: Atmosphere refuses the upgrade with a 501 ({@code X-Atmosphere-error: Websocket
 * protocol not supported}) and the client retries the WebSocket every 5 s for ever. Switch back to
 * {@code WEBSOCKET_XHR} once Foy serves {@code /VAADIN/push} over WebSocket.
 */
@StyleSheet(Lumo.STYLESHEET)
@Push(value = PushMode.AUTOMATIC, transport = Transport.LONG_POLLING)
public class DashboardAppShell implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.setPageTitle("forge-dashboard");
    }
}
