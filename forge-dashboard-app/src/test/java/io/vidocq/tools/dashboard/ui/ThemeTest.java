package io.vidocq.tools.dashboard.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dashboard's stylesheet: served with the app, darker than Lumo, and a rule for every class the views use. */
class ThemeTest {

    private static final String PATH = "/META-INF/resources/" + Theme.STYLESHEET;

    static String css() {
        try (InputStream in = DashboardAppShell.class.getResourceAsStream(PATH)) {
            assertNotNull(in, PATH + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Foy and Vaadin serve the application's META-INF/resources: the shell's relative URL reaches this file. */
    @Test
    void stylesheetIsAWebResourceOfTheApp() {
        assertNotNull(DashboardAppShell.class.getResource(PATH), PATH);
    }

    /** Option A: near-black text, dark secondary text, deeper status colours. */
    @Test
    void contrastColours() {
        String css = css();
        for (String token : List.of("--lumo-body-text-color: #0b0f14", "--lumo-secondary-text-color: #3a4250",
                "--lumo-success-color: #0a7a3d", "--lumo-error-color: #c81e1e", "--lumo-warning-color: #b45309")) {
            assertTrue(css.contains(token), token);
        }
    }

    /**
     * The underline of the selected org tab replaces Lumo's focus ring (both are box-shadows), and the toggle's frame
     * clips the buttons' outer ring: both get a ring of their own, or a keyboard user loses sight of the focus.
     */
    @Test
    void keyboardFocusStaysVisible() {
        // the production build minifies the stylesheet: ".a > b" becomes ".a>b"
        String css = css().replaceAll("\\s*>\\s*", ">");
        for (String selector : List.of(".dash-header vaadin-tab[selected][focus-ring]",
                ".dash-toggle>vaadin-button[focus-ring]", ".dash-toggle>vaadin-button[theme~=\"primary\"][focus-ring]")) {
            assertTrue(css.contains(selector), "no focus ring for " + selector);
        }
    }

    /** Every pill's text against its background, the zero pills included: WCAG AA for normal text, 4.5:1. */
    @Test
    void pillsAreReadable() {
        for (String pill : List.of("dash-pill-ok", "dash-pill-err", "dash-pill-warn", "dash-pill-pending",
                "dash-pill-zero")) {
            Map<String, String> rule = rule("." + pill);
            double ratio = contrast(colour(rule.get("color")), colour(rule.get("background")));
            assertTrue(ratio >= 4.5, pill + ": " + String.format("%.2f", ratio) + ":1");
        }
    }

    /** A Theme constant left out of CLASSES would escape the check that it has a rule. */
    @Test
    void everyDashConstantIsListed() throws IllegalAccessException {
        for (Field field : Theme.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class
                    && ((String) field.get(null)).startsWith("dash-")) {
                assertTrue(Theme.CLASSES.contains((String) field.get(null)), field.getName() + " is not in CLASSES");
            }
        }
    }

    /** The declarations of the rules whose selector list holds {@code selector}, later ones winning. */
    private static Map<String, String> rule(String selector) {
        Map<String, String> declarations = new HashMap<>();
        Matcher m = Pattern.compile("([^{}]+)\\{([^}]*)}").matcher(css().replaceAll("(?s)/\\*.*?\\*/", ""));
        while (m.find()) {
            if (List.of(m.group(1).split(",")).stream().map(String::strip).anyMatch(selector::equals)) {
                for (String declaration : m.group(2).split(";")) {
                    int colon = declaration.indexOf(':');
                    if (colon > 0) {
                        declarations.put(declaration.substring(0, colon).strip(), declaration.substring(colon + 1).strip());
                    }
                }
            }
        }
        assertTrue(!declarations.isEmpty(), "no rule for " + selector);
        return declarations;
    }

    /** A {@code #rrggbb} colour, or a {@code var(--token)} defined on {@code :root}. */
    private static String colour(String value) {
        assertNotNull(value, "a colour is missing");
        Matcher var = Pattern.compile("var\\((--[\\w-]+)\\)").matcher(value);
        return var.matches() ? colour(rule(":root").get(var.group(1))) : value;
    }

    private static double contrast(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        assertTrue(hex.matches("#[0-9a-fA-F]{6}"), hex + " is not #rrggbb");
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    /** A class or a grid part a view sets with no rule in the stylesheet would change nothing on screen. */
    @Test
    void everyThemeClassAndPartIsStyled() {
        String css = css();
        // ".dash-pill" must have its own rule, not only be the start of ".dash-pill-ok"
        Theme.CLASSES.forEach(name -> assertTrue(Pattern.compile("\\." + Pattern.quote(name) + "(?![\\w-])")
                .matcher(css).find(), "no rule for ." + name));
        Theme.PARTS.forEach(name -> assertTrue(css.contains("::part(" + name), "no rule for ::part(" + name + ")"));
    }
}
