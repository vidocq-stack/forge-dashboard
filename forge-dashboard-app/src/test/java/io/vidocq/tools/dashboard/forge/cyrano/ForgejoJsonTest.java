package io.vidocq.tools.dashboard.forge.cyrano;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.forge.ForgeJob;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real CodeFloe answers, captured once (mails redacted), mapped to the port's DTO. */
class ForgejoJsonTest {

    private static String read(String name) {
        try (var in = ForgejoJsonTest.class.getResourceAsStream("/fixtures/forgejo/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void runnerJobsMapToForgeJobs() {
        var jobs = ForgejoJson.toJobs(read("runner-jobs.json"));
        assertFalse(jobs.isEmpty());
        var j = jobs.getFirst();
        assertTrue(j.id() > 0 && j.runId() > 0 && j.repoId() > 0);
        assertTrue(Set.of("waiting", "running").contains(j.status()));
        assertTrue(j.htmlUrl().startsWith("https://codefloe.com/"));
        assertFalse(j.runsOn().isEmpty());
    }

    @Test
    void runnerMapsUuidStatusAndLabels() {
        var runners = ForgejoJson.toRunners(read("runners.json"));
        assertEquals(7, runners.size());
        for (var r : runners) {
            assertFalse(r.uuid().isBlank());
            assertTrue(Set.of("idle", "active", "offline").contains(r.status()), r.status());
            assertFalse(r.labels().isEmpty());
        }
        assertEquals("87c30ab4-ebf0-4d81-bd76-1126048f2d0e", runners.getFirst().uuid());
    }

    @Test
    void runMapsPrettyRefAndEvent() {
        var run = ForgejoJson.toRun(read("run.json"));
        assertEquals("pull_request", run.event());
        assertEquals("#299", run.prettyRef());
        assertFalse(run.commitSha().isBlank());
        assertEquals(Instant.parse("2026-11-14T11:58:33Z"), run.started());
        assertEquals(Instant.parse("2026-11-14T12:00:02Z"), run.stopped());
    }

    @Test
    void jobStepsGiveStartAndStop() {
        var job = ForgejoJson.toJob(read("job.json"));
        assertEquals(Instant.parse("2026-11-14T11:58:33Z"), job.startedAt());
        assertNotNull(job.stoppedAt());
        assertFalse(job.stoppedAt().isBefore(job.startedAt()));
        assertTrue(job.taskId() > 0);
    }

    @Test
    void jobWithoutStepsHasNoTimes() {
        var job = ForgejoJson.toJob("""
                {"id":1,"run_id":2,"repo_id":3,"task_id":0,"name":"skip","status":"skipped","runs_on":["docker"],
                 "html_url":"https://codefloe.com/o/r/actions/runs/9/jobs/0/attempt/1","needs":null}""");
        assertNull(job.startedAt());
        assertNull(job.stoppedAt());
    }

    @Test
    void runningJobHasNoStopTime() {
        ForgeJob job = ForgejoJson.toJob("""
                {"id":1,"run_id":2,"repo_id":3,"task_id":4,"name":"build","status":"running","runs_on":["docker"],
                 "html_url":"https://codefloe.com/o/r/actions/runs/9/jobs/0/attempt/1",
                 "steps":[{"number":0,"status":"success","started":"2026-11-14T10:00:00Z","stopped":"2026-11-14T10:00:05Z"},
                          {"number":1,"status":"running","started":"2026-11-14T10:00:05Z","stopped":"0001-01-01T00:00:00Z"}]}""");
        assertEquals(Instant.parse("2026-11-14T10:00:00Z"), job.startedAt());
        assertNull(job.stoppedAt());
    }

    @Test
    void commitsMapStatsAndLogin() {
        var commits = ForgejoJson.toCommits(read("commits.json"), "Vidocq", "vauban");
        assertEquals(10, commits.size());
        assertTrue(commits.stream().allMatch(c -> c.additions() >= 0 && c.deletions() >= 0));
        assertTrue(commits.stream().anyMatch(c -> c.authorLogin() != null));
        var first = commits.getFirst();
        assertEquals("Vidocq", first.org());
        assertEquals("vauban", first.repo());
        assertEquals(Instant.parse("2026-11-14T11:13:53Z"), first.authoredAt());
        assertEquals(189, first.additions());
        assertTrue(first.message().contains("Co-Authored-By"));
    }

    @Test
    void commitsAreFilteredOnCommitterDate() {
        // Forgejo 17 ignores ?since= on /commits. The 6th commit was written on the 13th and rebased on the 14th:
        // filtering on the author date would lose it.
        var since = Instant.parse("2026-11-14T10:00:00Z");
        var commits = ForgejoJson.toCommitsSince(read("commits.json"), "Vidocq", "vauban", since);
        assertEquals(6, commits.size());
        assertTrue(commits.getLast().authoredAt().isBefore(since));
        assertEquals(List.of(), ForgejoJson.toCommitsSince(read("commits.json"), "Vidocq", "vauban",
                Instant.parse("2026-11-15T00:00:00Z")));
    }

    @Test
    void combinedStatusMapsContexts() {
        var s = ForgejoJson.toCombinedStatus(read("status.json"));
        assertEquals(CiState.SUCCESS, s.state());
        assertEquals(28, s.contexts().size());
        // Each status carries its value in "status" ("state" is null); a skipped job counts as no state.
        assertEquals(27, s.contexts().stream().filter(c -> c.state() == CiState.SUCCESS).count());
        assertEquals(1, s.contexts().stream().filter(c -> c.state() == CiState.NONE).count());
    }

    @Test
    void reposMapOwnerAndDefaultBranch() {
        var repos = ForgejoJson.toRepos(read("repos.json"));
        assertEquals(33, repos.size());
        assertTrue(repos.stream().allMatch(r -> "Vidocq".equals(r.owner()) && r.defaultBranch() != null));
        assertTrue(repos.stream().anyMatch(r -> "vauban".equals(r.name())));
    }

    @Test
    void pullsMapHeadAndAuthor() {
        var pulls = ForgejoJson.toPulls(read("pulls-open.json"));
        assertEquals(7, pulls.size());
        for (var p : pulls) {
            assertTrue(p.number() > 0);
            assertNotNull(p.authorLogin());
            assertFalse(p.headSha().isBlank());
            assertNotNull(p.createdAt());
            assertNull(p.mergedAt());
        }
    }

    @Test
    void commitPullGivesAuthorLogin() {
        assertNotNull(ForgejoJson.toPull(read("commit-pull.json")).authorLogin());
    }

    @Test
    void nullJobListMapsToEmpty() {
        assertEquals(List.of(), ForgejoJson.toJobs("null"));
        assertEquals(List.of(), ForgejoJson.toJobs(""));
    }
}
