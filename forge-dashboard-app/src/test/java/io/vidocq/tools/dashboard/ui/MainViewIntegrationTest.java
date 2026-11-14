package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import io.vidocq.tools.dashboard.collect.OrgRegistry;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.ui.cdi.VaubanInstantiator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The main view as Vaadin gets it, from Vauban through the instantiator, outside a session (no user). */
class MainViewIntegrationTest {

    @Test
    void mainViewIsABeanWithOneTabPerTrackedOrg() {
        InProcessApp.truncate("tracked_org");
        InProcessApp.bean(OrgRegistry.class).seedIfEmpty(List.of("Vidocq", "VidocqTools"));

        MainView view = new VaubanInstantiator(null).getOrCreate(MainView.class);

        Tabs tabs = descendants(view).filter(Tabs.class::isInstance).map(Tabs.class::cast).findFirst().orElseThrow();
        assertEquals(List.of("Vidocq", "VidocqTools"),
                tabs.getChildren().map(tab -> ((Tab) tab).getLabel()).toList());
        assertTrue(descendants(view).anyMatch(c -> c instanceof Span s && "Projets — Vidocq".equals(s.getText())),
                "the projects view of the first org is shown");
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(MainViewIntegrationTest::descendants));
    }
}
