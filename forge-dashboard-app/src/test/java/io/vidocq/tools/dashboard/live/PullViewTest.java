package io.vidocq.tools.dashboard.live;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullViewTest {

    private static PullView pull(String repo, String title) {
        return new PullView(repo, 17, title, "yblazart", "sha", CiState.FAILURE, List.of(), GateStatus.NONE,
                "https://codefloe.com/Vidocq/" + repo + "/pulls/17", Instant.EPOCH);
    }

    private static PullView titled(String title) {
        return pull("erasmus", title);
    }

    /** Forgejo's drafts: a title starting with its default prefixes "WIP:" or "[WIP]", in any case. */
    @Test
    void draftIsATitleStartingWithAWorkInProgressPrefix() {
        assertTrue(titled("WIP: the TCK drives the plan").draft());
        assertTrue(titled("[WIP] pages").draft());
        assertTrue(titled("wip: lower case").draft());
        assertFalse(titled("fix: WIP: not at the start").draft());
        assertFalse(titled("WIPE the cache").draft());
        assertFalse(titled(null).draft());
    }

    /**
     * The drafts of the ecosystem are written "WIP : build(mcp): …", "WIP docs: …", "WIP ci(tassis): …": the word WIP
     * at the start, whatever follows it, is a draft too; a word that only starts with WIP is not.
     */
    @Test
    void draftIsATitleStartingWithTheWordWip() {
        assertTrue(titled("WIP : build(mcp): langchain4j-cdi 1.5.0").draft());
        assertTrue(titled("WIP docs: refresh README and ROADMAP").draft());
        assertTrue(titled("WIP ci(tassis): block the release").draft());
        assertTrue(titled("WIP").draft());
        assertTrue(titled("  WIP: leading spaces").draft());
        assertFalse(titled("WIPEOUT: a word").draft());
        assertFalse(titled("Wiping the cache").draft());
    }

    /** After a WIP marker, the leading tags are the components: "[WIP] [NOETHER] fix: …" gives NOETHER. */
    @Test
    void tagsFollowTheWipMarker() {
        PullView pull = pull("vidocq", "[WIP] [NOETHER] fix(persistence): flush (#123)");
        assertEquals(List.of("NOETHER"), pull.tags());
        assertEquals(List.of("noether"), pull.components());
        assertEquals("fix(persistence): flush (#123)", pull.shortTitle());
        assertTrue(pull.draft());
    }

    /** The tags of a title can be glued or separated by spaces. */
    @Test
    void severalTagsWithOrWithoutSpaces() {
        for (String title : new String[]{"[VAUBAN][CASSINI] refactor(spi): rename",
                "[VAUBAN] [CASSINI] refactor(spi): rename"}) {
            PullView pull = pull("vidocq", title);
            assertEquals(List.of("VAUBAN", "CASSINI"), pull.tags());
            assertEquals(List.of("vauban", "cassini"), pull.components());
            assertEquals("refactor(spi): rename", pull.shortTitle());
        }
    }

    /** Without a tag, the component of a pull request is its repository. */
    @Test
    void withoutTagTheComponentIsTheRepository() {
        PullView pull = pull("vauban", "feat: proxies");
        assertEquals(List.of(), pull.tags());
        assertEquals(List.of("vauban"), pull.components());
        assertEquals("feat: proxies", pull.shortTitle());
    }

    /** The WIP marker leaves the short title, whatever follows it. */
    @Test
    void theWipMarkerLeavesTheShortTitle() {
        assertEquals("build(mcp): x", pull("vidocq", "WIP: build(mcp): x").shortTitle());
        assertEquals("docs", pull("vidocq", "WIP : docs").shortTitle());
        assertEquals("docs: y", pull("vidocq", "WIP docs: y").shortTitle());
        assertEquals("h3", pull("vidocq", "[WIP] h3").shortTitle());
    }

    /** Forgejo's own draft prefix, « WIP: », does not hide the tags that follow it. */
    @Test
    void aTagAfterAWipColon() {
        PullView colon = pull("vidocq", "WIP: [NOETHER] fix(x): y");
        assertEquals(List.of("NOETHER"), colon.tags());
        assertEquals("fix(x): y", colon.shortTitle());
        PullView bracketColon = pull("vidocq", "[WIP]: [NOETHER] y");
        assertEquals(List.of("NOETHER"), bracketColon.tags());
        assertEquals("y", bracketColon.shortTitle());
    }

    /** A tag is only at the start of the title, and in upper case. */
    @Test
    void onlyALeadingTagIsAComponent() {
        assertEquals(List.of(), pull("vidocq", "fix: handle [FOY] header").tags());
        assertEquals(List.of(), pull("vidocq", "[foy] x").tags());
    }

    /** When the markers leave nothing, the short title is the whole title. */
    @Test
    void nothingLeftKeepsTheWholeTitle() {
        assertEquals("[WIP]", pull("vidocq", "[WIP]").shortTitle());
        PullView runtime = pull("vidocq", "[RUNTIME]");
        assertEquals(List.of("RUNTIME"), runtime.tags());
        assertEquals("[RUNTIME]", runtime.shortTitle());
    }

    private static PullView touching(String title, List<String> fileTags) {
        return pull("vidocq", title).withFileTags(fileTags);
    }

    /** The components of the changed files join the title's tags: the title's first, then the others. */
    @Test
    void theFilesAddTheComponentsTheTitleForgets() {
        PullView pull = touching("[VAUBAN] fix: x", List.of("CASSINI", "VAUBAN", "FOY"));
        assertEquals(List.of("VAUBAN"), pull.tags());
        assertEquals(List.of("vauban", "cassini", "foy"), pull.components());
        assertEquals(List.of(new ComponentTag("VAUBAN", true, true), new ComponentTag("CASSINI", false, true),
                new ComponentTag("FOY", false, true)), pull.componentTags());
    }

    /** A tag the files do not touch ([CI], [TCK], or a component the PR does not change) stays, from the title. */
    @Test
    void aTitleTagWithoutFilesStays() {
        PullView pull = touching("[CI] [FOY] ci: x", List.of());
        assertEquals(List.of(new ComponentTag("CI", true, false), new ComponentTag("FOY", true, false)),
                pull.componentTags());
        assertEquals(List.of("ci", "foy"), pull.components());
    }

    /** Without a tag in the title, the files' components replace the repository. */
    @Test
    void withoutTagTheFilesGiveTheComponents() {
        PullView pull = touching("fix: x", List.of("NOETHER"));
        assertEquals(List.of("noether"), pull.components());
        assertEquals(List.of(new ComponentTag("NOETHER", false, true)), pull.componentTags());
        assertEquals("fix: x", pull.shortTitle());
    }

    /** Without tag nor files, the repository, as before; the 10-argument constructor has no file tags. */
    @Test
    void withoutTagNorFilesTheRepository() {
        PullView pull = pull("vauban", "feat: proxies");
        assertEquals(List.of(), pull.fileTags());
        assertEquals(List.of(), pull.componentTags());
        assertEquals(List.of("vauban"), pull.components());
    }

    /** The Sonar gate changes, the file tags stay. */
    @Test
    void theSonarGateKeepsTheFileTags() {
        PullView pull = touching("fix: x", List.of("FOY")).withSonarGate(GateStatus.OK);
        assertEquals(GateStatus.OK, pull.sonarGate());
        assertEquals(List.of("FOY"), pull.fileTags());
    }

    /** A pull request restored without a title has no tag, its repository as component, an empty short title. */
    @Test
    void aPullWithoutTitle() {
        PullView pull = pull("erasmus", null);
        assertEquals(List.of(), pull.tags());
        assertEquals(List.of("erasmus"), pull.components());
        assertEquals("", pull.shortTitle());
    }
}
