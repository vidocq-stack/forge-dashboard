package io.vidocq.tools.dashboard.live;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullViewTest {

    private static PullView titled(String title) {
        return new PullView("erasmus", 17, title, "yblazart", "sha", CiState.FAILURE, List.of(), GateStatus.NONE,
                "https://codefloe.com/Vidocq/erasmus/pulls/17", Instant.EPOCH);
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
}
