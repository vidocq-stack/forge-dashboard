package io.vidocq.tools.dashboard.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    /** A class or a grid part a view sets with no rule in the stylesheet would change nothing on screen. */
    @Test
    void everyThemeClassAndPartIsStyled() {
        String css = css();
        Theme.CLASSES.forEach(name -> assertTrue(css.contains("." + name), "no rule for ." + name));
        Theme.PARTS.forEach(name -> assertTrue(css.contains("::part(" + name), "no rule for ::part(" + name + ")"));
    }
}
