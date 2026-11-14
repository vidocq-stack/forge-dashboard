package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.domain.LogHead;
import io.vidocq.tools.dashboard.store.RunnerRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static io.vidocq.tools.dashboard.collect.ForgeFixtures.job;
import static io.vidocq.tools.dashboard.collect.ForgeFixtures.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JobResolverTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final Instant HEAD_AT = Instant.parse("2026-11-14T11:58:33.506107Z");

    private static RunnerRow row(long id, String uuid) {
        var r = new RunnerRow();
        r.setId(id);
        r.setUuid(uuid);
        return r;
    }

    @Test
    void prNumberFromPrettyRef() {
        assertEquals(298, JobResolver.prNumber(run("pull_request", "#298")));
        assertEquals(12, JobResolver.prNumber(run("pull_request_target", "#12")));
        assertNull(JobResolver.prNumber(run("push", "main")));
        assertNull(JobResolver.prNumber(null));
    }

    @Test
    void repoFromHtmlUrl() {
        assertEquals(Optional.of("vidocq"),
                JobResolver.repoOf("https://codefloe.com/Vidocq/vidocq/actions/runs/3906/jobs/0/attempt/1"));
        assertEquals(Optional.empty(), JobResolver.repoOf("https://codefloe.com/Vidocq"));
        assertEquals(Optional.empty(), JobResolver.repoOf(null));
    }

    @Test
    void resolveUsesLogHeadForRunnerAndStart() {
        var head = Optional.of(new LogHead("U", 175_790, HEAD_AT));
        var e = JobResolver.resolve("Vidocq", job(175_790, "running"), run("pull_request", "#298"), "vidocq", head,
                Map.of("U", row(1219, "U")), NOW);
        assertEquals(1219L, e.getRunnerId());
        assertEquals(HEAD_AT, e.getStartedAt());
        assertEquals(175_790L, e.getTaskId());
        assertEquals(298, e.getPrNumber());
        assertEquals("vidocq", e.getRepo());
        assertEquals("Vidocq", e.getOrg());
        assertEquals("#298", e.getRef());
        assertEquals("running", e.getStatus());
        assertNull(e.getStoppedAt());
    }

    @Test
    void resolveWithoutHeadStartsNowWithoutRunner() {
        var e = JobResolver.resolve("Vidocq", job(175_790, "running"), run("push", "main"), "vidocq",
                Optional.empty(), Map.of(), NOW);
        assertNull(e.getRunnerId());
        assertEquals(NOW, e.getStartedAt());
        assertNull(e.getPrNumber());
    }

    @Test
    void headOfAnotherTaskOrUnknownRunnerIsIgnored() {
        var otherTask = Optional.of(new LogHead("U", 1, HEAD_AT));
        var e = JobResolver.resolve("Vidocq", job(175_790, "running"), null, "vidocq", otherTask,
                Map.of("U", row(1219, "U")), NOW);
        assertNull(e.getRunnerId());
        assertEquals(NOW, e.getStartedAt());

        var unknownRunner = Optional.of(new LogHead("X", 175_790, HEAD_AT));
        var f = JobResolver.resolve("Vidocq", job(175_790, "running"), null, "vidocq", unknownRunner,
                Map.of("U", row(1219, "U")), NOW);
        assertNull(f.getRunnerId());
        assertEquals(HEAD_AT, f.getStartedAt());
    }
}
