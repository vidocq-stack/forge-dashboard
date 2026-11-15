package io.vidocq.tools.dashboard.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The texts of the dashboard in one language, from {@code i18n/messages_<tag>.properties} (English:
 * {@code i18n/messages.properties}), UTF-8, in {@link MessageFormat} syntax where an apostrophe is written as is.
 *
 * <p>The files are read by this module itself rather than through {@code ResourceBundle.getBundle}, which the module
 * system restricts. A key missing from a language takes its English text; a key missing from English is a bug.
 */
public final class Messages {

    /** The languages of the dashboard, English first: it is the fallback of any other language. */
    public static final List<Language> LANGUAGES = List.of(
            new Language("en", "English"),
            new Language("de", "Deutsch"),
            new Language("es", "Español"),
            new Language("fr", "Français"),
            new Language("it", "Italiano"),
            new Language("nl", "Nederlands"),
            new Language("pl", "Polski"));

    private static final String ENGLISH = "en";

    private static final Map<String, Properties> FILES = LANGUAGES.stream()
            .collect(Collectors.toUnmodifiableMap(Language::tag, l -> load(l.tag())));

    private static final Map<String, Messages> BY_TAG = LANGUAGES.stream()
            .collect(Collectors.toUnmodifiableMap(Language::tag, l -> new Messages(l.tag())));

    private final Locale locale;
    private final Properties texts;

    private Messages(String tag) {
        this.locale = Locale.forLanguageTag(tag);
        this.texts = FILES.get(tag);
    }

    /** The texts in the language of {@code locale} ({@code de-CH} gets German), English for any other language. */
    public static Messages forLocale(Locale locale) {
        Messages found = locale == null ? null : BY_TAG.get(locale.getLanguage());
        return found != null ? found : BY_TAG.get(ENGLISH);
    }

    /**
     * The texts in the best language of an {@code Accept-Language} header, by its weights and by language alone
     * ({@code nl-BE} gets Dutch); English when the header is absent, malformed, or names none of the languages.
     */
    public static Messages forAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return BY_TAG.get(ENGLISH);
        }
        List<Locale.LanguageRange> ranges;
        try {
            ranges = Locale.LanguageRange.parse(header);
        } catch (IllegalArgumentException malformed) {
            return BY_TAG.get(ENGLISH);
        }
        return ranges.stream()
                .filter(r -> r.getWeight() > 0)
                .map(r -> BY_TAG.get(r.getRange().split("-", 2)[0]))
                .filter(m -> m != null)
                .findFirst()
                .orElse(BY_TAG.get(ENGLISH));
    }

    public Locale locale() {
        return locale;
    }

    /** The text of {@code key}, its {@code {0}}, {@code {1}}… replaced by {@code args}. */
    public String text(String key, Object... args) {
        String pattern = texts.getProperty(key);
        if (pattern == null) {
            pattern = FILES.get(ENGLISH).getProperty(key);
        }
        if (pattern == null) {
            throw new IllegalArgumentException("No text for " + key);
        }
        return new MessageFormat(pattern.replace("'", "''"), locale).format(args);
    }

    /** The keys of one language file, for the completeness test. */
    static Set<String> keys(String tag) {
        return FILES.get(tag).stringPropertyNames();
    }

    /** The text of {@code key} as written in one language file, {@code null} when that file lacks it. */
    static String raw(String tag, String key) {
        return FILES.get(tag).getProperty(key);
    }

    private static Properties load(String tag) {
        String path = ENGLISH.equals(tag) ? "/i18n/messages.properties" : "/i18n/messages_" + tag + ".properties";
        try (InputStream in = Messages.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + path);
            }
            Properties texts = new Properties();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                texts.load(reader);
            }
            return texts;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
