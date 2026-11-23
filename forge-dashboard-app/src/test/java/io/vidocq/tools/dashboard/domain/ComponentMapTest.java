package io.vidocq.tools.dashboard.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentMapTest {

    private static final ComponentMap MAP = ComponentMap.parse("""
            {"description": "the components", "components": [
              {"tag": "PARENT", "path": "vidocq-parent", "group": "build", "level": "stable"},
              {"tag": "VAUBAN", "path": "components/core/vauban"},
              {"tag": "FOY", "path": "components/web/foy"},
              {"tag": "CORE", "path": "components/core"}
            ]}""");

    private static String fixture(String name) {
        try (var in = ComponentMapTest.class.getResourceAsStream("/fixtures/forgejo/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A file belongs to the component whose folder holds it. */
    @Test
    void aFileInAFolderGivesItsComponent() {
        assertEquals(List.of("FOY"), MAP.tagsOf(List.of("components/web/foy/src/Main.java")));
    }

    /** Files outside every folder give no component. */
    @Test
    void aFileOutsideTheFoldersGivesNone() {
        assertEquals(List.of(), MAP.tagsOf(List.of(".forgejo/workflows/ci.yml", ".gitignore", "README.md")));
    }

    /** Nested folders: the longest one wins. */
    @Test
    void theLongestFolderWins() {
        assertEquals(List.of("VAUBAN"), MAP.tagsOf(List.of("components/core/vauban/pom.xml")));
        assertEquals(List.of("CORE"), MAP.tagsOf(List.of("components/core/pom.xml")));
    }

    /** "vidocq-parent-old/x" is not in "vidocq-parent": a folder ends at a "/". */
    @Test
    void aPrefixIsNotAFolder() {
        assertEquals(List.of(), MAP.tagsOf(List.of("vidocq-parent-old/pom.xml", "components/web/foyer/a")));
    }

    /** Each component once, in the order of components.json. */
    @Test
    void tagsInTheOrderOfTheMapOnceEach() {
        assertEquals(List.of("VAUBAN", "FOY"), MAP.tagsOf(List.of("components/web/foy/a", "components/core/vauban/b",
                "components/web/foy/c")));
    }

    @Test
    void anUnreadableMapIsEmpty() {
        assertTrue(ComponentMap.parse("not json").isEmpty());
        assertTrue(ComponentMap.parse("{\"components\": null}").isEmpty());
        assertTrue(ComponentMap.parse("[]").isEmpty());
        assertTrue(ComponentMap.EMPTY.tagsOf(List.of("components/web/foy/a")).isEmpty());
    }

    /** The components.json of the monorepo and the files of its pull request #766. */
    @Test
    void theMonorepoMapReadsARealPullRequest() {
        ComponentMap map = ComponentMap.parse(fixture("components.json"));
        assertFalse(map.isEmpty());
        assertEquals(List.of("VAUBAN"), map.tagsOf(List.of(
                "vidocq-components/vidocq-components-jakartaee/vidocq-components-jakartaee-core/vauban/BUG.md")));
        assertEquals(List.of("RUNTIME"), map.tagsOf(List.of("vidocq-runtime/pom.xml")));
    }
}
