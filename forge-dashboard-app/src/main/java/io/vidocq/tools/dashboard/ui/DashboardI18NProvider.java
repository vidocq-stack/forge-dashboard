package io.vidocq.tools.dashboard.ui;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.i18n.I18NProvider;
import io.vidocq.tools.dashboard.i18n.Messages;

/**
 * The dashboard's languages and texts for Vaadin, which picks the UI language among them from the browser and falls
 * back to the first one, English. The views take their texts from {@link Messages} directly.
 */
public final class DashboardI18NProvider implements I18NProvider {

    private static final List<Locale> LOCALES = Messages.LANGUAGES.stream()
            .map(l -> Locale.forLanguageTag(l.tag()))
            .toList();

    @Override
    public List<Locale> getProvidedLocales() {
        return LOCALES;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        return Messages.forLocale(locale).text(key, params);
    }
}
