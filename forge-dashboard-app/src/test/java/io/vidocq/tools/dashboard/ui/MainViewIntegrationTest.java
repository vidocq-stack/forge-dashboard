package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import io.vidocq.tools.dashboard.collect.OrgRegistry;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.ui.cdi.VaubanInstantiator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static io.vidocq.tools.dashboard.ui.ProjectsPanelIntegrationTest.visibleHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The main view as Vaadin gets it, from Vauban through the instantiator, outside a session (no user). */
class MainViewIntegrationTest {

    private static final List<String> NARROW = List.of("Repo", "main", "Gate", "PR");
    private static final List<String> WIDE = List.of("Repo", "main", "LOC", "Tests", "Couv.", "Gate", "PR",
            "Dernier commit");

    @Test
    void mainViewIsABeanWithOneTabPerTrackedOrg() {
        MainView view = mainView();

        Tabs tabs = descendants(view).filter(Tabs.class::isInstance).map(Tabs.class::cast).findFirst().orElseThrow();
        assertEquals(List.of("Vidocq", "VidocqTools"),
                tabs.getChildren().map(tab -> ((Tab) tab).getLabel()).toList());
        assertTrue(descendants(view).anyMatch(ProjectsPanel.class::isInstance),
                "the projects view of the first org is shown");
    }

    /** On a phone the view toggle takes its own row under the org tabs, and each panel shown is the narrow one. */
    @Test
    void narrowScreenPutsTheToggleOnItsOwnRowAndNarrowsEachPanel() {
        MainView view = mainView();
        // Not CI: OccupancyService caches the occupancy of Vidocq, which OccupancyServiceIntegrationTest then reads
        Button committers = button(view, "Commiters");
        Button projects = button(view, "Projets");

        view.narrow(true);
        assertEquals("100%", toggleRow(projects).getElement().getStyle().get("flex-basis"));
        assertEquals(NARROW, visibleHeaders(view));

        committers.click();
        projects.click();
        assertEquals(NARROW, visibleHeaders(view), "a panel created on a phone is narrow too");

        view.narrow(false);
        assertNull(toggleRow(projects).getElement().getStyle().get("flex-basis"));
        assertEquals(WIDE, visibleHeaders(view));
    }

    private static MainView mainView() {
        InProcessApp.truncate("tracked_org");
        InProcessApp.bean(OrgRegistry.class).seedIfEmpty(List.of("Vidocq", "VidocqTools"));
        return new VaubanInstantiator(null).getOrCreate(MainView.class);
    }

    private static Button button(Component root, String text) {
        return descendants(root).filter(c -> c instanceof Button b && text.equals(b.getText())).map(Button.class::cast)
                .findFirst().orElseThrow();
    }

    private static Component toggleRow(Button button) {
        return button.getParent().orElseThrow();
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(MainViewIntegrationTest::descendants));
    }
}
