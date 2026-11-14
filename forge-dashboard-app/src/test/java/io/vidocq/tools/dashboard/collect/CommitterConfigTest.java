package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.config.CommitterConfig;
import io.vidocq.tools.dashboard.domain.CommitKind;
import io.vidocq.tools.dashboard.domain.RawCommit;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommitterConfigTest {

    private static RawCommit by(String name, String mail) {
        return new RawCommit("s", "Vidocq", "vauban", Instant.EPOCH, name, mail, null, "fix: x", 1, 1);
    }

    @Test
    void configuredBotsAddToTheDefaults() {
        var classifier = new CommitterConfig(" renovate , dependabot ", "").classifier();
        assertEquals(CommitKind.BOT, classifier.classify(by("renovate", "r@x")).kind());
        assertEquals(CommitKind.BOT, classifier.classify(by("vidocq-bot", "b@x")).kind());
        assertEquals(CommitKind.HUMAN, classifier.classify(by("Someone", "s@x")).kind());
    }

    @Test
    void aliasesMapNamesAndMailsToLogins() {
        var classifier = new CommitterConfig("", "Yann Blazart=yblazart, Y@Example.org=yblazart, broken").classifier();
        assertEquals("yblazart", classifier.classify(by("Yann Blazart", "other@x")).humanKey());
        assertEquals("yblazart", classifier.classify(by("Y. B.", "y@example.org")).humanKey());
    }

    @Test
    void absentConfigurationUsesTheDefaults() {
        var classifier = new CommitterConfig(null, null).classifier();
        assertEquals(CommitKind.BOT, classifier.classify(by("Vidocq Bot", "b@x")).kind());
        assertEquals("s@x", classifier.classify(by("Someone", "s@x")).humanKey());
    }
}
