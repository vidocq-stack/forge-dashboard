package io.vidocq.tools.dashboard.ui;

import java.util.Objects;
import java.util.Set;

import io.vidocq.tools.dashboard.i18n.Messages;

/**
 * What a user last chose: the view, the period of the statistics (7, 30 or 90 days), the org tab and the language
 * ({@code null}: the browser's). A missing view, another period or a language the dashboard does not have falls back
 * to the defaults.
 */
public record UserPreferences(String login, ViewMode view, int periodDays, String lastOrg, String language) {

    private static final Set<Integer> PERIODS = Set.of(7, 30, 90);
    private static final int DEFAULT_PERIOD = 30;

    public UserPreferences {
        Objects.requireNonNull(login, "login");
        view = view == null ? ViewMode.PROJECTS : view;
        periodDays = PERIODS.contains(periodDays) ? periodDays : DEFAULT_PERIOD;
        String tag = language;
        language = Messages.LANGUAGES.stream().anyMatch(l -> l.tag().equals(tag)) ? tag : null;
    }

    public static UserPreferences defaults(String login) {
        return new UserPreferences(login, ViewMode.PROJECTS, DEFAULT_PERIOD, null, null);
    }

    public UserPreferences withView(ViewMode view) {
        return new UserPreferences(login, view, periodDays, lastOrg, language);
    }

    public UserPreferences withPeriodDays(int periodDays) {
        return new UserPreferences(login, view, periodDays, lastOrg, language);
    }

    public UserPreferences withLastOrg(String lastOrg) {
        return new UserPreferences(login, view, periodDays, lastOrg, language);
    }

    /** {@code null} goes back to the browser's language. */
    public UserPreferences withLanguage(String language) {
        return new UserPreferences(login, view, periodDays, lastOrg, language);
    }
}
