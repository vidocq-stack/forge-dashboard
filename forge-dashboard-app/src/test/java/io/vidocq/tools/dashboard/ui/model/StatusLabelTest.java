package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.i18n.Messages;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusLabelTest {

    @Test
    void knownStatusesAreTranslated() {
        assertEquals("échec", StatusLabel.of("failure", Messages.forLocale(Locale.FRENCH)));
        assertEquals("läuft", StatusLabel.of("running", Messages.forLocale(Locale.GERMAN)));
        assertEquals("geslaagd", StatusLabel.of("success", Messages.forLocale(Locale.forLanguageTag("nl"))));
    }

    /** A status a later Forgejo adds shows as it comes, rather than a key or nothing. */
    @Test
    void unknownStatusShowsAsIs() {
        assertEquals("weird", StatusLabel.of("weird", Messages.forLocale(Locale.FRENCH)));
        assertEquals("", StatusLabel.of(null, Messages.forLocale(Locale.FRENCH)));
    }
}
