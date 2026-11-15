package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.shared.Registration;
import io.vidocq.tools.dashboard.auth.DashboardUser;
import io.vidocq.tools.dashboard.collect.CollectorScheduler;
import io.vidocq.tools.dashboard.collect.OrgAdmin;
import io.vidocq.tools.dashboard.collect.OrgRegistry;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.ui.model.Screen;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The dashboard: one tab per tracked org, the toggle {@code Projects | CI | Committers} in the middle of the header,
 * the freshness badge and the user menu on the right, and the panel of the current view for the current org below.
 * The view, the period, the org tab and the language come back from the user's preferences; each change is saved. The
 * language is the browser's (Vaadin picks it among the dashboard's) unless the user chose one in the avatar menu. On a
 * phone ({@link Screen#narrow}) the toggle takes its own row and the tables keep their essential columns.
 */
@Route("")
@PageTitle("forge-dashboard")
@Dependent
public class MainView extends VerticalLayout {

    private final UserPrefs prefs;
    private final OrgRegistry orgs;
    private final OrgAdmin admin;
    private final Instance<ProjectsPanel> projects;
    private final Instance<CiPanel> ci;
    private final Instance<CommittersPanel> committers;
    private final Optional<DashboardUser> user;
    private final Messages messages;
    private final Tabs orgTabs = new Tabs();
    private final Map<Tab, String> tabOrgs = new LinkedHashMap<>();
    private final Tab addOrg = new Tab("+");
    private final Map<ViewMode, Button> toggle = new EnumMap<>(ViewMode.class);
    private final Div content = new Div();
    private final HorizontalLayout header;
    private final HorizontalLayout toggleRow;
    private final HorizontalLayout right;
    private UserPreferences current;
    private String org;
    private Component shown;
    private boolean narrow;
    private Registration resize;

    @Inject
    public MainView(ChangeBus bus, UserPrefs prefs, OrgRegistry orgs, CollectorScheduler collector, Clock clock,
            Instance<ProjectsPanel> projects, Instance<CiPanel> ci, Instance<CommittersPanel> committers,
            OrgAdmin admin) {
        this.prefs = prefs;
        this.orgs = orgs;
        this.admin = admin;
        this.projects = projects;
        this.ci = ci;
        this.committers = committers;
        this.user = CurrentUser.get();
        this.current = user.map(u -> prefs.load(u.login())).orElseGet(() -> UserPreferences.defaults("?"));
        UI ui = UI.getCurrent();
        // before any text: the panels take the language of the UI when they are built
        if (ui != null && current.language() != null) {
            ui.setLocale(Locale.forLanguageTag(current.language()));
        } else if (ui != null && VaadinRequest.getCurrent() != null) {
            // Vaadin prefers an exact match anywhere in the browser's list (fr-CA, en-US, en → en); the dashboard
            // takes the first language it has, by weight, as the sign-in pages do
            ui.setLocale(Messages.forAcceptLanguage(VaadinRequest.getCurrent().getHeader("Accept-Language")).locale());
        }
        this.messages = Texts.current();

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        addOrg.getElement().setAttribute("title", messages.text("header.followOrg"));
        orgTabs.addSelectedChangeListener(e -> {
            if (!e.isFromClient()) {
                return;
            }
            if (e.getSelectedTab() == addOrg) {
                orgTabs.setSelectedTab(e.getPreviousTab());
                user.ifPresent(u -> new OrgAdminDialog(admin, u, this::orgAdded).open());
            } else if (tabOrgs.containsKey(e.getSelectedTab())) {
                org = tabOrgs.get(e.getSelectedTab());
                save(current.withLastOrg(org));
                showContent();
            }
        });
        // shrink to the left section and scroll, rather than slide under the badge (a flex item keeps its content
        // width by default)
        orgTabs.getStyle().set("min-width", "0");
        HorizontalLayout left = section(orgTabs);
        right = section(new FreshnessBadge(collector::health, clock, bus), userMenu());
        right.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        toggleRow = viewToggle();
        header = new HorizontalLayout(left, toggleRow, right);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.addClassName(Theme.HEADER);

        content.setSizeFull();
        content.getStyle().set("box-sizing", "border-box");
        add(header, content);
        setFlexGrow(1, content);

        narrow = ui != null && Screen.narrow(windowWidth(ui));
        layout();
        refreshTabs();
        LiveBinding.bind(this, bus, e -> e instanceof ChangeEvent.OrgsChanged, this::refreshTabs);
        addAttachListener(e -> {
            // the page came with the browser's language (DashboardI18NProvider); the user's choice may differ
            e.getUI().getPage().executeJs("document.documentElement.lang = $0", messages.locale().toLanguageTag());
            narrow(Screen.narrow(windowWidth(e.getUI())));
            resize = e.getUI().getPage().addBrowserWindowResizeListener(r -> narrow(Screen.narrow(r.getWidth())));
        });
        addDetachListener(e -> {
            if (resize != null) {
                resize.remove();
                resize = null;
            }
        });
    }

    /** The browser's {@code window.innerWidth}, sent when the page starts; -1 if unknown. */
    private static int windowWidth(UI ui) {
        ExtendedClientDetails details = ui.getPage().getExtendedClientDetails();
        return details == null ? -1 : details.getWindowInnerWidth();
    }

    /**
     * The layout for a phone ({@code true}: the view toggle on its own row under the org tabs, the columns that
     * matter at a glance) or a wider screen; the panel shown is kept, with what the user unfolded in it.
     */
    void narrow(boolean narrow) {
        if (narrow == this.narrow) {
            return;
        }
        this.narrow = narrow;
        layout();
        if (shown instanceof ProjectsPanel panel) {
            panel.narrow(narrow);
        } else if (shown instanceof CommittersPanel panel) {
            panel.narrow(narrow);
        }
    }

    private void layout() {
        String gutter = narrow ? "var(--lumo-space-s)" : "var(--lumo-space-m)";
        header.getStyle().set("padding", "0 " + gutter);
        content.getStyle().set("padding", gutter);
        onNarrow(header, "flex-wrap", "wrap");
        // order 1: after the org tabs and the user menu, which share the first row
        onNarrow(toggleRow, "order", "1");
        onNarrow(toggleRow, "flex-basis", "100%");
        onNarrow(toggleRow, "justify-content", "center");
        onNarrow(toggleRow, "padding-bottom", "var(--lumo-space-s)");
        right.getStyle().set("flex", narrow ? "0 0 auto" : "1 1 0");
    }

    private void onNarrow(Component component, String property, String value) {
        if (narrow) {
            component.getElement().getStyle().set(property, value);
        } else {
            component.getElement().getStyle().remove(property);
        }
    }

    /** Left and right parts of the header, of equal width so that the toggle stays in the middle. */
    private static HorizontalLayout section(Component... components) {
        HorizontalLayout section = new HorizontalLayout(components);
        section.setAlignItems(FlexComponent.Alignment.CENTER);
        section.getStyle().set("flex", "1 1 0").set("min-width", "0");
        return section;
    }

    private HorizontalLayout viewToggle() {
        HorizontalLayout group = Parts.toggle();
        for (ViewMode mode : ViewMode.values()) {
            Button button = new Button(messages.text(mode.key()), e -> {
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
        SubMenu items = menu.addItem(avatar).getSubMenu();
        SubMenu languages = items.addItem(messages.text("header.language")).getSubMenu();
        languageItem(languages, messages.text("header.language.browser"), null);
        Messages.LANGUAGES.forEach(l -> languageItem(languages, l.name(), l.tag()));
        items.addItem(messages.text("header.signOut"), e -> UI.getCurrent().getPage().setLocation("/oauth2/logout"));
        return menu;
    }

    /** A language of the menu, checked when it is the user's choice ({@code null}: the browser's); reloads in it. */
    private void languageItem(SubMenu languages, String text, String tag) {
        MenuItem item = languages.addItem(text, e -> {
            save(current.withLanguage(tag));
            UI.getCurrent().getPage().reload();
        });
        item.setCheckable(true);
        item.setChecked(Objects.equals(tag, current.language()));
    }

    /** The org tabs from the registry; the selected org stays when it is still tracked, else the saved one or the first. */
    private void refreshTabs() {
        List<String> active = orgs.activeOrgs();
        String wanted = org != null ? org : current.lastOrg();
        String selected = active.contains(wanted) ? wanted : active.isEmpty() ? null : active.getFirst();
        orgTabs.removeAll();
        tabOrgs.clear();
        boolean isAdmin = user.map(DashboardUser::admin).orElse(false);
        for (String name : active) {
            Tab tab = new Tab(name);
            tabOrgs.put(tab, name);
            orgTabs.add(tab);
            if (name.equals(selected)) {
                orgTabs.setSelectedTab(tab);
            }
            if (isAdmin) {
                tab.getElement().setAttribute("title", messages.text("header.unfollowHint", name));
                new ContextMenu(tab).addItem(messages.text("header.unfollow", name), e -> orgRemoved(name));
            }
        }
        if (isAdmin) {
            orgTabs.add(addOrg);
        }
        org = selected;
        showContent();
    }

    /** A new panel of the current view for the current org; the one replaced is detached, so unsubscribed. */
    private void showContent() {
        content.removeAll();
        shown = null;
        if (org == null) {
            content.add(new Span(messages.text("header.noOrg")));
            return;
        }
        shown = switch (current.view()) {
            case PROJECTS -> projects.get().narrow(narrow).forOrg(org);
            case CI -> ci.get().forOrg(org);
            case COMMITTERS -> committers.get().narrow(narrow).forOrg(org, current.periodDays(),
                    days -> save(current.withPeriodDays(days)));
        };
        content.add(shown);
    }

    /** Shows the org just added; the other UIs get it through {@code OrgsChanged}. */
    private void orgAdded(String name) {
        org = name;
        save(current.withLastOrg(name));
        refreshTabs();
    }

    /** Off the tabs (its data is kept); the first org is shown if it was the current one. */
    private void orgRemoved(String name) {
        user.ifPresent(u -> admin.deactivate(name, u));
        if (name.equals(org)) {
            org = null;
            save(current.withLastOrg(null));
        }
        refreshTabs();
    }

    private void save(UserPreferences next) {
        current = next;
        user.ifPresent(u -> prefs.save(next));
    }
}
