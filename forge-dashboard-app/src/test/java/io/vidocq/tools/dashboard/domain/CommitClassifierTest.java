package io.vidocq.tools.dashboard.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.vidocq.tools.dashboard.domain.CommitClassifier.DEFAULT_BOTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitClassifierTest {

    private final CommitClassifier classifier = new CommitClassifier(DEFAULT_BOTS, Map.of("Yann Blazart", "yblazart"));

    private static RawCommit commit(String authorName, String authorLogin, String message) {
        return new RawCommit("abc123", "Vidocq", "vidocq", Instant.parse("2026-11-14T10:00:00Z"), authorName,
                "someone@example.org", authorLogin, message, 10, 2);
    }

    @Test
    void agentFamiliesAreNormalised() {
        assertEquals(Optional.of("Claude Opus"), CommitClassifier.agentFamily("Claude Opus 5.5"));
        assertEquals(Optional.of("Claude Opus"), CommitClassifier.agentFamily("Claude Opus 5 (1M context)"));
        assertEquals(Optional.of("Claude Fable"), CommitClassifier.agentFamily("Claude Fable 5.2"));
        assertEquals(Optional.of("Claude Sonnet"), CommitClassifier.agentFamily("Claude Sonnet 5.5"));
        assertEquals(Optional.of("Claude Haiku"), CommitClassifier.agentFamily("Claude Haiku 5.5"));
        assertEquals(Optional.of("Codex"), CommitClassifier.agentFamily("OpenAI Codex"));
        assertEquals(Optional.of("Codex"), CommitClassifier.agentFamily("Codex"));
        assertEquals(Optional.of("Copilot"), CommitClassifier.agentFamily("Copilot App"));
        assertEquals(Optional.of("Copilot"), CommitClassifier.agentFamily("Copilot"));
        assertEquals(Optional.of("Mistral Vibe"), CommitClassifier.agentFamily("Mistral Vibe"));
        assertEquals(Optional.of("Gemini"), CommitClassifier.agentFamily("Gemini"));
        assertEquals(Optional.of("Cursor"), CommitClassifier.agentFamily("Cursor"));
        assertEquals(Optional.empty(), CommitClassifier.agentFamily("Antoine Sabot-Durand"));
    }

    @Test
    void bareClaudeIsAnAgentFamily() {
        assertEquals(Optional.of("Claude"), CommitClassifier.agentFamily("Claude"));
    }

    @Test
    void humanWithAgentTrailerAndSession() {
        var c = commit("Yann Blazart", "yblazart", """
                fix: x

                Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                Claude-Session: https://claude.ai/code/session_X
                """);
        var k = classifier.classify(c);
        assertEquals(CommitKind.HUMAN, k.kind());
        assertEquals("yblazart", k.humanKey());
        assertEquals(List.of("Claude Opus"), k.agentFamilies());
        assertEquals("https://claude.ai/code/session_X", k.claudeSession());
        assertEquals(List.of("Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>",
                "Claude-Session: https://claude.ai/code/session_X"), k.rawTrailers());
    }

    @Test
    void lowercaseTrailerIsRead() {
        var k = classifier.classify(commit("Yann Blazart", "yblazart", "fix: y\n\nCo-authored-by: Copilot <x>\n"));
        assertEquals(List.of("Copilot"), k.agentFamilies());
    }

    @Test
    void unknownCoAuthorIsNotAnAgent() {
        var k = classifier.classify(commit("Yann Blazart", "yblazart",
                "fix: z\n\nCo-Authored-By: Antoine Sabot-Durand <asd@example.org>\n"));
        assertEquals(CommitKind.HUMAN, k.kind());
        assertEquals(List.of(), k.agentFamilies());
        assertEquals(1, k.rawTrailers().size());
    }

    @Test
    void sameFamilyTwiceIsCountedOnce() {
        var k = classifier.classify(commit("Yann Blazart", "yblazart", """
                fix: w

                Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
                Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
                Co-Authored-By: OpenAI Codex <codex@openai.com>
                """));
        assertEquals(List.of("Claude Opus", "Codex"), k.agentFamilies());
    }

    @Test
    void sabotDurandIsNotABot() {
        var k = classifier.classify(commit("Antoine Sabot-Durand", "asabotdurand", "feat: a\n"));
        assertEquals(CommitKind.HUMAN, k.kind());
        assertEquals("asabotdurand", k.humanKey());
    }

    @Test
    void plainBotIsBot() {
        var k = classifier.classify(commit("vidocq-bot", null, "chore: release\n"));
        assertEquals(CommitKind.BOT, k.kind());
        assertNull(k.humanKey());
        assertTrue(k.agentFamilies().isEmpty());
    }

    @Test
    void botWithAgentTrailerIsAgentViaBot() {
        var k = classifier.classify(commit("Vidocq Bot", null,
                "fix: b\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n"));
        assertEquals(CommitKind.AGENT_VIA_BOT, k.kind());
        assertNull(k.humanKey());
        assertEquals(List.of("Claude Opus"), k.agentFamilies());
    }

    @Test
    void aliasResolvesHumanWithoutLogin() {
        var k = classifier.classify(commit("Yann Blazart", null, "fix: c\n"));
        assertEquals(CommitKind.HUMAN, k.kind());
        assertEquals("yblazart", k.humanKey());
    }

    @Test
    void humanWithoutLoginNorAliasIsKeyedByLowercasedMail() {
        var c = new RawCommit("d", "Vidocq", "vidocq", Instant.EPOCH, "Jane Doe", "Jane.Doe@Example.org", null,
                "fix: d\n", 1, 1);
        assertEquals("jane.doe@example.org", classifier.classify(c).humanKey());
    }

    @Test
    void messageWithoutTrailersHasNone() {
        var k = classifier.classify(commit("Yann Blazart", "yblazart", "fix: no trailers"));
        assertTrue(k.rawTrailers().isEmpty());
        assertNull(k.claudeSession());
    }
}
