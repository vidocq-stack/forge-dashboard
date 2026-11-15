package io.vidocq.tools.dashboard.ui;

import io.vidocq.tools.dashboard.store.UserPref;
import io.vidocq.tools.dashboard.store.UserPrefRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The preferences of a user, in the PostgreSQL of the dev services. */
class UserPrefsIntegrationTest {

    @BeforeEach
    void reset() {
        InProcessApp.truncate("user_pref");
    }

    @Test
    void prefsRoundTrip() {
        UserPrefs prefs = InProcessApp.bean(UserPrefs.class);
        UserPreferences saved = new UserPreferences("yblazart", ViewMode.CI, 7, "VidocqTools", null);

        prefs.save(saved);

        assertEquals(saved, prefs.load("yblazart"));
        assertEquals(UserPreferences.defaults("inconnu"), prefs.load("inconnu"));
    }

    @Test
    void savingAgainReplacesThePreferences() {
        UserPrefs prefs = InProcessApp.bean(UserPrefs.class);
        prefs.save(new UserPreferences("yblazart", ViewMode.CI, 7, "VidocqTools", null));

        prefs.save(new UserPreferences("yblazart", ViewMode.COMMITTERS, 90, null, null));

        assertEquals(new UserPreferences("yblazart", ViewMode.COMMITTERS, 90, null, null), prefs.load("yblazart"));
    }

    @Test
    void defaultsAreProjectsOverThirtyDays() {
        assertEquals(new UserPreferences("yblazart", ViewMode.PROJECTS, 30, null, null), UserPreferences.defaults("yblazart"));
    }

    @Test
    void oddValuesFallBackToDefaults() {
        assertEquals(UserPreferences.defaults("yblazart"), new UserPreferences("yblazart", null, 14, null, null));
    }

    @Test
    void languageChoiceIsSaved() {
        UserPrefs prefs = InProcessApp.bean(UserPrefs.class);

        prefs.save(UserPreferences.defaults("yblazart").withLanguage("de"));

        assertEquals("de", prefs.load("yblazart").language());
    }

    /** No choice means the browser's language. */
    @Test
    void browserIsTheDefault() {
        assertNull(InProcessApp.bean(UserPrefs.class).load("yblazart").language());
    }

    /** A language the dashboard no longer has, saved earlier, means the browser's language again. */
    @Test
    void unsupportedSavedLanguageMeansBrowser() {
        UserPref row = new UserPref();
        row.setLogin("yblazart");
        row.setViewMode("CI");
        row.setPeriodDays(7);
        row.setLanguage("xx");
        InProcessApp.bean(UserPrefRepository.class).save(row);

        UserPreferences loaded = InProcessApp.bean(UserPrefs.class).load("yblazart");

        assertNull(loaded.language());
        assertEquals(ViewMode.CI, loaded.view());
    }

    @Test
    void otherChoicesKeepTheLanguage() {
        UserPreferences german = UserPreferences.defaults("yblazart").withLanguage("de");

        assertEquals("de", german.withView(ViewMode.CI).withPeriodDays(7).withLastOrg("Vidocq").language());
        assertNull(german.withLanguage(null).language());
    }
}
