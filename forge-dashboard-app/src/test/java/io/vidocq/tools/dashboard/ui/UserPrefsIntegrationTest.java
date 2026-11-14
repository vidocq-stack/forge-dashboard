package io.vidocq.tools.dashboard.ui;

import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The preferences of a user, in the PostgreSQL of the dev services. */
class UserPrefsIntegrationTest {

    @BeforeEach
    void reset() {
        InProcessApp.truncate("user_pref");
    }

    @Test
    void prefsRoundTrip() {
        UserPrefs prefs = InProcessApp.bean(UserPrefs.class);
        UserPreferences saved = new UserPreferences("yblazart", ViewMode.CI, 7, "VidocqTools");

        prefs.save(saved);

        assertEquals(saved, prefs.load("yblazart"));
        assertEquals(UserPreferences.defaults("inconnu"), prefs.load("inconnu"));
    }

    @Test
    void savingAgainReplacesThePreferences() {
        UserPrefs prefs = InProcessApp.bean(UserPrefs.class);
        prefs.save(new UserPreferences("yblazart", ViewMode.CI, 7, "VidocqTools"));

        prefs.save(new UserPreferences("yblazart", ViewMode.COMMITTERS, 90, null));

        assertEquals(new UserPreferences("yblazart", ViewMode.COMMITTERS, 90, null), prefs.load("yblazart"));
    }

    @Test
    void defaultsAreProjectsOverThirtyDays() {
        assertEquals(new UserPreferences("yblazart", ViewMode.PROJECTS, 30, null), UserPreferences.defaults("yblazart"));
    }

    @Test
    void oddValuesFallBackToDefaults() {
        assertEquals(UserPreferences.defaults("yblazart"), new UserPreferences("yblazart", null, 14, null));
    }
}
