package io.vidocq.tools.dashboard.ui;

import java.util.Objects;
import java.util.Set;

/**
 * What a user last chose: the view, the period of the statistics (7, 30 or 90 days) and the org tab. A missing view
 * or another period falls back to the defaults.
 */
public record UserPreferences(String login, ViewMode view, int periodDays, String lastOrg) {

    private static final Set<Integer> PERIODS = Set.of(7, 30, 90);
    private static final int DEFAULT_PERIOD = 30;

    public UserPreferences {
        Objects.requireNonNull(login, "login");
        view = view == null ? ViewMode.PROJECTS : view;
        periodDays = PERIODS.contains(periodDays) ? periodDays : DEFAULT_PERIOD;
    }

    public static UserPreferences defaults(String login) {
        return new UserPreferences(login, ViewMode.PROJECTS, DEFAULT_PERIOD, null);
    }

    public UserPreferences withView(ViewMode view) {
        return new UserPreferences(login, view, periodDays, lastOrg);
    }

    public UserPreferences withPeriodDays(int periodDays) {
        return new UserPreferences(login, view, periodDays, lastOrg);
    }

    public UserPreferences withLastOrg(String lastOrg) {
        return new UserPreferences(login, view, periodDays, lastOrg);
    }
}
