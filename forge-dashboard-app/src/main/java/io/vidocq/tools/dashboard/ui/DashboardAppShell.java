package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;

/**
 * Application shell, with server push.
 * <p>
 * WORKAROUND vidocq#65: long polling until Foy implements Jakarta WebSocket. With {@code WEBSOCKET_XHR} the client
 * does not fall back on its own: Chappe answers the upgrade with a 404 and the client retries the WebSocket every
 * 5 s for ever. Switch back to {@code WEBSOCKET_XHR} once Foy serves {@code /VAADIN/push} over WebSocket.
 */
@Push(value = PushMode.AUTOMATIC, transport = Transport.LONG_POLLING)
public class DashboardAppShell implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.setPageTitle("forge-dashboard");
    }
}
