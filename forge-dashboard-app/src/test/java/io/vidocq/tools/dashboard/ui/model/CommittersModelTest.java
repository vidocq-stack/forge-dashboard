package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CommitKind;
import io.vidocq.tools.dashboard.store.CommitterAggregate;
import io.vidocq.tools.dashboard.store.PrCounts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommittersModelTest {

    private static CommitterAggregate agg(String key, CommitKind kind, String agents, long commits) {
        return new CommitterAggregate(key, kind, agents, commits, commits * 10, commits * 2);
    }

    @Test
    void humansAutomationAndUnattributedAreSeparated() {
        CommittersView v = CommittersModel.of(List.of(agg("yblazart", CommitKind.HUMAN, null, 10),
                agg("vidocq-bot", CommitKind.BOT, null, 5),
                agg("?agent", CommitKind.AGENT_VIA_BOT, "Claude Opus", 2)), List.of());

        assertEquals(List.of("yblazart"), v.humans().stream().map(CommitterRow::humanKey).toList());
        assertEquals(List.of("vidocq-bot"), v.automation().stream().map(CommitterRow::humanKey).toList());
        assertNotNull(v.unattributed());
        assertEquals(2, v.unattributed().commits());
    }

    @Test
    void noUnattributedCommitMeansNoLine() {
        assertNull(CommittersModel.of(List.of(agg("yblazart", CommitKind.HUMAN, null, 1)), List.of()).unattributed());
    }

    @Test
    void breakdownStartsWithAloneThenByVolume() {
        CommitterRow row = CommittersModel.of(List.of(agg("yblazart", CommitKind.HUMAN, "Codex", 5),
                agg("yblazart", CommitKind.HUMAN, null, 10),
                agg("yblazart", CommitKind.HUMAN, "Claude Opus", 30)), List.of()).humans().getFirst();

        assertEquals(List.of("seul", "Claude Opus", "Codex"), List.copyOf(row.breakdown().keySet()));
        assertEquals(45, row.commits());
        assertEquals(450, row.additions());
        assertEquals(90, row.deletions());
    }

    @Test
    void agentViaBotResolvedJoinsHuman() {
        CommitterRow row = CommittersModel.of(List.of(agg("yblazart", CommitKind.HUMAN, null, 10),
                agg("yblazart", CommitKind.AGENT_VIA_BOT, "Claude Opus", 4)), List.of()).humans().getFirst();
        assertEquals(14, row.commits());
        assertEquals(4L, row.breakdown().get("Claude Opus"));
    }

    @Test
    void multiAgentCommitsCountOnceButInEachFamily() {
        CommitterRow row = CommittersModel.of(List.of(agg("yblazart", CommitKind.HUMAN, "Claude Opus,Codex", 3)),
                List.of()).humans().getFirst();
        assertEquals(3, row.commits());
        assertEquals(3L, row.breakdown().get("Claude Opus"));
        assertEquals(3L, row.breakdown().get("Codex"));
    }

    @Test
    void prCountsAreJoined() {
        CommittersView v = CommittersModel.of(List.of(agg("yblazart", CommitKind.HUMAN, null, 10),
                agg("asabot", CommitKind.HUMAN, null, 2)), List.of(new PrCounts("yblazart", 7, 5)));
        CommitterRow yann = v.humans().getFirst();
        assertEquals(7, yann.prsOpened());
        assertEquals(5, yann.prsMerged());
        assertEquals(0, v.humans().get(1).prsOpened());
    }

    @Test
    void sortedByCommitsDescending() {
        CommittersView v = CommittersModel.of(List.of(agg("a", CommitKind.HUMAN, null, 1),
                agg("b", CommitKind.HUMAN, null, 9), agg("c", CommitKind.HUMAN, null, 4)), List.of());
        assertEquals(List.of("b", "c", "a"), v.humans().stream().map(CommitterRow::humanKey).toList());
    }
}
