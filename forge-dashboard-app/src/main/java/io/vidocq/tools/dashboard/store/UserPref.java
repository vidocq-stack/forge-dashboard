package io.vidocq.tools.dashboard.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The preferences of a signed-in user: view ({@code PROJECTS|CI|COMMITTERS}), period in days, last org tab, and
 * language tag ({@code null}: the browser's).
 */
@Entity
@Table(name = "user_pref")
public class UserPref {

    @Id
    private String login;
    private String viewMode;
    private Integer periodDays;
    private String lastOrg;
    private String language;

    public UserPref() {
    }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }
    public String getViewMode() { return viewMode; }
    public void setViewMode(String viewMode) { this.viewMode = viewMode; }
    public Integer getPeriodDays() { return periodDays; }
    public void setPeriodDays(Integer periodDays) { this.periodDays = periodDays; }
    public String getLastOrg() { return lastOrg; }
    public void setLastOrg(String lastOrg) { this.lastOrg = lastOrg; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
