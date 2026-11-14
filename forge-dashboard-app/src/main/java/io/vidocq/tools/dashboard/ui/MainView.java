package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.vidocq.tools.dashboard.auth.DashboardUser;
import io.vidocq.tools.dashboard.collect.CollectorScheduler;
import io.vidocq.tools.dashboard.collect.OrgRegistry;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The dashboard: one tab per tracked org, the toggle {@code Projets | CI | Commiters} in the middle of the header,
 * the freshness badge and the user menu on the right, and the panel of the current view for the current org below.
 * The view, the period and the org tab come back from the user's preferences; each change is saved.
 */
@Route("")
@PageTitle("forge-dashboard")
@Dependent
public class MainView extends VerticalLayout {

    private final UserPrefs prefs;
    private final OrgRegistry orgs;
    private final Instance<ProjectsPanel> projects;
    private final Optional<DashboardUser> user;
    private final Tabs orgTabs = new Tabs();
    private final Map<Tab, String> tabOrgs = new LinkedHashMap<>();
    private final Map<ViewMode, Button> toggle = new EnumMap<>(ViewMode.class);
    private final Div content = new Div();
    private UserPreferences current;
    private String org;

    @Inject
    public MainView(ChangeBus bus, UserPrefs prefs, OrgRegistry orgs, CollectorScheduler collector, Clock clock,
            Instance<ProjectsPanel> projects) {
        this.prefs = prefs;
        this.orgs = orgs;
        this.projects = projects;
        this.user = CurrentUser.get();
        this.current = user.map(u -> prefs.load(u.login())).orElseGet(() -> UserPreferences.defaults("?"));

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        orgTabs.addSelectedChangeListener(e -> {
            if (e.isFromClient() && tabOrgs.containsKey(e.getSelectedTab())) {
                org = tabOrgs.get(e.getSelectedTab());
                save(current.withLastOrg(org));
                showContent();
            }
        });
        HorizontalLayout left = section(orgTabs);
        HorizontalLayout right = section(new FreshnessBadge(collector::health, clock, bus), userMenu());
        right.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        HorizontalLayout header = new HorizontalLayout(left, viewToggle(), right);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("padding", "0 var(--lumo-space-m)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        content.setSizeFull();
        content.getStyle().set("padding", "var(--lumo-space-m)").set("box-sizing", "border-box");
        add(header, content);
        setFlexGrow(1, content);

        refreshTabs();
        LiveBinding.bind(this, bus, e -> e instanceof ChangeEvent.OrgsChanged, this::refreshTabs);
    }

    /** Left and right parts of the header, of equal width so that the toggle stays in the middle. */
    private static HorizontalLayout section(Component... components) {
        HorizontalLayout section = new HorizontalLayout(components);
        section.setAlignItems(FlexComponent.Alignment.CENTER);
        section.getStyle().set("flex", "1 1 0").set("min-width", "0");
        return section;
    }

    private Component viewToggle() {
        HorizontalLayout group = new HorizontalLayout();
        group.setSpacing(false);
        group.getStyle().set("gap", "2px");
        for (ViewMode mode : ViewMode.values()) {
            Button button = new Button(mode.label(), e -> {
                save(current.withView(mode));
                markToggle();
                showContent();
            });
            toggle.put(mode, button);
            group.add(button);
        }
        markToggle();
        return group;
    }

    private void markToggle() {
        toggle.forEach((mode, button) -> {
            button.removeThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
            button.addThemeVariants(mode == current.view() ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
        });
    }

    private Component userMenu() {
        MenuBar menu = new MenuBar();
        menu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        Avatar avatar = new Avatar(user.map(DashboardUser::name).orElse("?"));
        user.map(DashboardUser::pictureUrl).ifPresent(avatar::setImage);
        avatar.getElement().setAttribute("title", user.map(DashboardUser::login).orElse(""));
        menu.addItem(avatar).getSubMenu()
                .addItem("Se déconnecter", e -> UI.getCurrent().getPage().setLocation("/oauth2/logout"));
        return menu;
    }

    /** The org tabs from the registry; the selected org stays when it is still tracked, else the saved one or the first. */
    private void refreshTabs() {
        List<String> active = orgs.activeOrgs();
        String wanted = org != null ? org : current.lastOrg();
        String selected = active.contains(wanted) ? wanted : active.isEmpty() ? null : active.getFirst();
        orgTabs.removeAll();
        tabOrgs.clear();
        for (String name : active) {
            Tab tab = new Tab(name);
            tabOrgs.put(tab, name);
            orgTabs.add(tab);
            if (name.equals(selected)) {
                orgTabs.setSelectedTab(tab);
            }
        }
        org = selected;
        showContent();
    }

    /** A new panel of the current view for the current org; the one replaced is detached, so unsubscribed. */
    private void showContent() {
        content.removeAll();
        if (org == null) {
            content.add(new Span("Aucune organisation suivie."));
            return;
        }
        content.add(switch (current.view()) {
            case PROJECTS -> projects.get().forOrg(org);
            case CI, COMMITTERS -> new Span(current.view().label() + " — " + org);   // Tasks 14 and 15
        });
    }

    private void save(UserPreferences next) {
        current = next;
        user.ifPresent(u -> prefs.save(next));
    }
}
