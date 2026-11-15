package io.vidocq.tools.dashboard.ui;

import io.vidocq.tools.dashboard.store.UserPref;
import io.vidocq.tools.dashboard.store.UserPrefRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The preferences of the users, one row of {@code user_pref} per CodeFloe login. */
@ApplicationScoped
public class UserPrefs {

    private final UserPrefRepository repository;

    @Inject
    public UserPrefs(UserPrefRepository repository) {
        this.repository = repository;
    }

    /**
     * The saved preferences, or the defaults for a user who never chose; a view or a language no longer known is the
     * default.
     */
    public UserPreferences load(String login) {
        return repository.findById(login)
                .map(row -> new UserPreferences(login, view(row.getViewMode()),
                        row.getPeriodDays() == null ? 0 : row.getPeriodDays(), row.getLastOrg(),
                        row.getLanguage()))
                .orElseGet(() -> UserPreferences.defaults(login));
    }

    public void save(UserPreferences preferences) {
        UserPref row = new UserPref();
        row.setLogin(preferences.login());
        row.setViewMode(preferences.view().name());
        row.setPeriodDays(preferences.periodDays());
        row.setLastOrg(preferences.lastOrg());
        row.setLanguage(preferences.language());
        repository.save(row);
    }

    private static ViewMode view(String name) {
        try {
            return name == null ? null : ViewMode.valueOf(name);
        } catch (IllegalArgumentException renamed) {
            return null;
        }
    }
}
