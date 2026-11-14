package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.lumo.Lumo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardAppShellTest {

    /** Vaadin 25 applies no theme by default: without Lumo the views lose every {@code --lumo-*} colour and font. */
    @Test
    void shellLinksTheLumoStylesheet() {
        List<String> links = new ArrayList<>();
        AppShellSettings settings = new AppShellSettings() {
            @Override
            public void addLink(String rel, String href) {
                links.add(rel + " " + href);
            }
        };

        new DashboardAppShell().configurePage(settings);

        assertTrue(links.contains("stylesheet " + Lumo.STYLESHEET), "links: " + links);
    }
}
