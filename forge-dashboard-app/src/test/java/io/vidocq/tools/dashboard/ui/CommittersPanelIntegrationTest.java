package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.domain.CommitKind;
import io.vidocq.tools.dashboard.store.CommitStat;
import io.vidocq.tools.dashboard.store.CommitStatRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static io.vidocq.tools.dashboard.ui.ProjectsPanelIntegrationTest.visibleHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The committers panel as the main view gets it from Vauban. */
class CommittersPanelIntegrationTest {

    @Test
    void orgWithoutCommitsSaysSo() {
        CommittersPanel panel = InProcessApp.bean(CommittersPanel.class).forOrg("Nobody", 30, days -> { });
        assertTrue(descendants(panel).anyMatch(c -> c instanceof Span s
                && s.getText().equals("Aucun commit sur 30 jours dans Nobody.")), "the empty state is shown");
    }

    /** On a phone: who, how many commits, what share; the lines, PRs and agents bar (and its legend) come back wider. */
    @Test
    void narrowScreenKeepsCommitterCommitsAndShare() {
        InProcessApp.truncate("commit_stat");
        Instant twoDaysAgo = InProcessApp.bean(Clock.class).instant().minus(Duration.ofDays(2));
        InProcessApp.bean(CommitStatRepository.class).save(commit("narrow1", twoDaysAgo));

        CommittersPanel panel = InProcessApp.bean(CommittersPanel.class).narrow(true)
                .forOrg("NarrowOrg", 30, days -> { });
        assertEquals(List.of("Commiter", "Commits", "Part"), visibleHeaders(panel));
        assertTrue(descendants(panel).noneMatch(CommittersPanelIntegrationTest::isLegend), "no legend without the bar");

        panel.narrow(false);
        assertEquals(List.of("Commiter", "Commits", "Part", "Lignes", "PR ouvertes / mergées", "Avec agents"),
                visibleHeaders(panel));
        assertTrue(descendants(panel).anyMatch(CommittersPanelIntegrationTest::isLegend), "the legend is back");
    }

    private static boolean isLegend(Component c) {
        return CommittersPanel.AGENT_SHARE_NOTE.equals(c.getElement().getAttribute("title"));
    }

    private static CommitStat commit(String sha, Instant at) {
        var c = new CommitStat();
        c.setId("NarrowOrg/repo@" + sha);
        c.setSha(sha);
        c.setOrg("NarrowOrg");
        c.setRepo("repo");
        c.setAuthoredAt(at);
        c.setAuthorName("alice");
        c.setKind(CommitKind.HUMAN);
        c.setHumanKey("alice");
        c.setAgentFamilies("Claude Opus");
        c.setAdditions(10);
        c.setDeletions(2);
        return c;
    }

    private static Stream<Component> descendants(Component root) {
        return Stream.concat(Stream.of(root), root.getChildren().flatMap(CommittersPanelIntegrationTest::descendants));
    }
}
