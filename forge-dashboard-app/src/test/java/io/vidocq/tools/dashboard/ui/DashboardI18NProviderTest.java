package io.vidocq.tools.dashboard.ui;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.i18n.I18NProvider;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.ui.cdi.VaubanInstantiator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DashboardI18NProviderTest {

    /** Vaadin picks the UI language among the provided ones, and falls back to the first: English. */
    @Test
    void vaadinGetsTheSevenLanguagesEnglishFirst() {
        I18NProvider provider = new VaubanInstantiator(null).getI18NProvider();

        assertInstanceOf(DashboardI18NProvider.class, provider);
        assertEquals(List.of("en", "de", "es", "fr", "it", "nl", "pl"),
                provider.getProvidedLocales().stream().map(Locale::getLanguage).toList());
    }

    @Test
    void translatesThroughMessages() {
        I18NProvider provider = new VaubanInstantiator(null).getI18NProvider();

        assertEquals(Messages.forLocale(Locale.GERMAN).text("ci.noRunners", "X"),
                provider.getTranslation("ci.noRunners", Locale.GERMAN, "X"));
    }
}
