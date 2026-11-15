package io.vidocq.tools.dashboard.i18n;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessagesTest {

    /** English first, the fallback and Vaadin's default; the others by their own name. */
    @Test
    void theSevenLanguagesEnglishFirst() {
        assertEquals(List.of("en", "de", "es", "fr", "it", "nl", "pl"),
                Messages.LANGUAGES.stream().map(Language::tag).toList());
        assertEquals(List.of("English", "Deutsch", "Español", "Français", "Italiano", "Nederlands", "Polski"),
                Messages.LANGUAGES.stream().map(Language::name).toList());
    }

    @Test
    void spanishItalianAndPolishFromTheBrowser() {
        assertEquals("Proyectos", Messages.forAcceptLanguage("es-MX,es;q=0.9").text("view.PROJECTS"));
        assertEquals("Progetti", Messages.forAcceptLanguage("it-CH").text("view.PROJECTS"));
        assertEquals("Projekty", Messages.forAcceptLanguage("pl-PL,pl;q=0.9,en;q=0.8").text("view.PROJECTS"));
    }

    @Test
    void unsupportedLanguageFallsBackToEnglish() {
        assertEquals("en", Messages.forLocale(Locale.forLanguageTag("pt")).locale().getLanguage());
    }

    /** A Swiss German or a Belgian browser gets German or Dutch, not the English fallback. */
    @Test
    void regionalVariantGetsItsLanguage() {
        assertEquals("de", Messages.forLocale(Locale.forLanguageTag("de-CH")).locale().getLanguage());
        assertEquals("nl", Messages.forAcceptLanguage("pt;q=1, nl-BE;q=0.8, fr;q=0.5").locale().getLanguage());
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
