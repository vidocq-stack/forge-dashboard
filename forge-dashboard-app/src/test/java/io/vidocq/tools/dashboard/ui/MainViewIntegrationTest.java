package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import io.vidocq.tools.dashboard.auth.DashboardUser;
import io.vidocq.tools.dashboard.collect.OrgRegistry;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.ui.cdi.VaubanInstantiator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static io.vidocq.tools.dashboard.ui.ProjectsPanelIntegrationTest.visibleHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The main view as Vaadin gets it, from Vauban through the instantiator, in a UI of a given language. */
class MainViewIntegrationTest {

    private static final List<String> NARROW = List.of("Repo", "main", "Gate", "PR");
    private static final List<String> WIDE = List.of("Repo", "main", "LOC", "Tests", "Couv.", "Gate", "PR",
            "Dernier commit");
    private static final DashboardUser ALICE = new DashboardUser("alice", "Alice", null, Set.of(), false);

    @BeforeEach
    void reset() {
        InProcessApp.truncate("user_pref");
    }

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
        TestUi.run(Locale.FRENCH, () -> {
            MainView view = mainView();
            // Not CI: OccupancyService caches the occupancy of Vidocq, which OccupancyServiceIntegrationTest reads
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
        });
    }

    /**
     * The org tabs shrink to their share of the header and scroll, rather than slide under the freshness badge: on a
     * phone, "aktualisiert vor 4 s" left them less room than "frais il y a 4 s".
     */
    @Test
    void orgTabsShrinkInsteadOfOverflowingTheBadge() {
        Tabs tabs = descendants(mainView()).filter(Tabs.class::isInstance).map(Tabs.class::cast).findFirst()
                .orElseThrow();
        assertEquals("0", tabs.getStyle().get("min-width"));
    }

    /** The stylesheet rules the header (no inline border any more) and frames the view buttons. */
    @Test
    void headerAndViewToggleCarryTheThemeClasses() {
        TestUi.run(Locale.FRENCH, () -> {
            Component toggle = toggleRow(button(mainView(), "Projets"));
            assertTrue(toggle.getElement().getClassList().contains("dash-toggle"), "the view buttons are a toggle");
            Component header = toggle.getParent().orElseThrow();
            assertTrue(header.getElement().getClassList().contains("dash-header"), "the header is themed");
            assertNull(header.getElement().getStyle().get("border-bottom"), "the stylesheet draws its rule");
        });
    }

    /** Without a choice, the language Vaadin took from the browser; a regional variant gets its language. */
    @Test
    void browserLanguageWithoutAChoice() {
        TestUi.run(Locale.forLanguageTag("nl-BE"), ALICE, () -> {
            MainView view = mainView();
            button(view, "Projecten");
            assertEquals(List.of("Repo", "main", "LOC", "Tests", "Dekking", "Gate", "PR", "Laatste commit"),
                    visibleHeaders(view));
        });
    }

    /**
     * Vaadin takes an exact match anywhere in the browser's list before a language match: for this Firefox list it
     * would pick English. The dashboard takes the first language it has, by weight, as the sign-in pages do.
     */
    @Test
    void firstBrowserLanguageWinsOverAnExactMatchFurtherDown() {
        TestUi.get(Locale.ENGLISH, ALICE, "fr-CA,en-US;q=0.7,en;q=0.3", () -> {
            MainView view = mainView();
            assertEquals("fr", UI.getCurrent().getLocale().getLanguage());
            return button(view, "Projets");
        });
    }

    @Test
    void savedLanguageWinsOverTheBrowser() {
        InProcessApp.bean(UserPrefs.class).save(UserPreferences.defaults("alice").withLanguage("de"));

        TestUi.run(Locale.FRENCH, ALICE, () -> {
            MainView view = mainView();
            assertEquals("de", UI.getCurrent().getLocale().getLanguage());
            button(view, "Projekte");
            assertEquals(List.of("Repo", "main", "LOC", "Tests", "Abd.", "Gate", "PR", "Letzter Commit"),
                    visibleHeaders(view));
        });
    }

    @Test
    void languageMenuOffersTheBrowserAndTheSevenLanguages() {
        TestUi.run(Locale.FRENCH, ALICE, () -> {
            List<MenuItem> items = languageItems(mainView());
            assertEquals(List.of("Navigateur", "English", "Deutsch", "Español", "Français", "Italiano", "Nederlands",
                    "Polski"), items.stream().map(MenuItem::getText).toList());
            assertEquals(List.of(true, false, false, false, false, false, false, false),
                    items.stream().map(MenuItem::isChecked).toList(),
                    "no choice yet: the browser's");
        });
    }

    @Test
    void choosingALanguageSavesItAndBrowserClearsIt() {
        TestUi.run(Locale.FRENCH, ALICE, () -> {
            List<MenuItem> items = languageItems(mainView());
            click(items.get(2));
            assertEquals("de", InProcessApp.bean(UserPrefs.class).load("alice").language());

            click(items.getFirst());
            assertNull(InProcessApp.bean(UserPrefs.class).load("alice").language());
        });
    }

    private static MainView mainView() {
        InProcessApp.truncate("tracked_org");
        InProcessApp.bean(OrgRegistry.class).seedIfEmpty(List.of("Vidocq", "VidocqTools"));
        return new VaubanInstantiator(null).getOrCreate(MainView.class);
    }

    /** The items of the avatar menu's language submenu, the browser's first. */
    private static List<MenuItem> languageItems(MainView view) {
        MenuBar menu = descendants(view).filter(MenuBar.class::isInstance).map(MenuBar.class::cast).findFirst()
                .orElseThrow();
        MenuItem language = menu.getItems().getFirst().getSubMenu().getItems().stream()
                .filter(i -> "Langue".equals(i.getText())).findFirst().orElseThrow();
        return language.getSubMenu().getItems();
    }

    private static void click(MenuItem item) {
        ComponentUtil.fireEvent(item, new ClickEvent<>(item));
    }

    private static Button button(Component root, String text) {
        return descendants(root).filter(c -> c instanceof Button b && text.equals(b.getText())).map(Button.class::cast)
                .findFirst().orElseThrow(() -> new AssertionError("no button " + text));
    }

    private static Component toggleRow(Button button) {
        return button.getParent().orElseThrow();
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(MainViewIntegrationTest::descendants));
    }
}
