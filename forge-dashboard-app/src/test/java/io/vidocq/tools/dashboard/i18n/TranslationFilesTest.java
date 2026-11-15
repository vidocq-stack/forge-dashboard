package io.vidocq.tools.dashboard.i18n;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** A missing translation breaks this test, not the screen: every file has the keys and the arguments of English. */
class TranslationFilesTest {

    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)");

    @ParameterizedTest
    @ValueSource(strings = {"fr", "de", "nl"})
    void sameKeysAsEnglish(String tag) {
        assertEquals(new TreeSet<>(Messages.keys("en")), new TreeSet<>(Messages.keys(tag)), "keys of messages_" + tag);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "de", "nl"})
    void sameArgumentsAsEnglish(String tag) {
        for (String key : Messages.keys("en")) {
            String translated = Messages.raw(tag, key);
            assertNotNull(translated, key + " is missing from messages_" + tag);
            assertEquals(arguments(Messages.raw("en", key)), arguments(translated),
                    "arguments of " + key + " in messages_" + tag);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "de", "nl"})
    void noEmptyText(String tag) {
        for (String key : Messages.keys(tag)) {
            assertFalse(Messages.raw(tag, key).isBlank(), key + " is empty in messages_" + tag);
        }
    }

    private static Set<String> arguments(String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = ARGUMENT.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }
}
