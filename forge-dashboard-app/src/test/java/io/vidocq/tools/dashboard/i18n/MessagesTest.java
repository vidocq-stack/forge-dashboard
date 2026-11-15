package io.vidocq.tools.dashboard.i18n;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagesTest {

    @Test
    void theFourLanguagesEnglishFirst() {
        assertEquals(List.of("en", "fr", "de", "nl"), Messages.LANGUAGES.stream().map(Language::tag).toList());
        assertEquals(List.of("English", "Français", "Deutsch", "Nederlands"),
                Messages.LANGUAGES.stream().map(Language::name).toList());
    }

    @Test
    void unsupportedLanguageFallsBackToEnglish() {
        assertEquals("en", Messages.forLocale(Locale.forLanguageTag("es")).locale().getLanguage());
    }

    /** A Swiss German or a Belgian browser gets German or Dutch, not the English fallback. */
    @Test
    void regionalVariantGetsItsLanguage() {
        assertEquals("de", Messages.forLocale(Locale.forLanguageTag("de-CH")).locale().getLanguage());
        assertEquals("nl", Messages.forAcceptLanguage("es;q=1, nl-BE;q=0.8, fr;q=0.5").locale().getLanguage());
    }

    @Test
    void missingOrMalformedAcceptLanguageIsEnglish() {
        assertEquals("en", Messages.forAcceptLanguage(null).locale().getLanguage());
        assertEquals("en", Messages.forAcceptLanguage("").locale().getLanguage());
        assertEquals("en", Messages.forAcceptLanguage("*;q=abc").locale().getLanguage());
    }

    @Test
    void apostropheAndArgumentRenderAsWritten() {
        assertEquals("Aucun runner pour Vidocq pour l'instant : la collecte arrive, ou le jeton ne lit pas les runners "
                + "de cette org.", Messages.forLocale(Locale.FRENCH).text("ci.noRunners", "Vidocq"));
    }

    @Test
    void unknownKeyIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Messages.forLocale(Locale.ENGLISH).text("no.such.key"));
    }
}
