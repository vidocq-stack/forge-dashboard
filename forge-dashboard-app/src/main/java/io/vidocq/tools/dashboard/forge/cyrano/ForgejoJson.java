package io.vidocq.tools.dashboard.forge.cyrano;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.domain.RawCommit;
import io.vidocq.tools.dashboard.forge.CombinedStatus;
import io.vidocq.tools.dashboard.forge.ForgeJob;
import io.vidocq.tools.dashboard.forge.ForgePull;
import io.vidocq.tools.dashboard.forge.ForgeRepo;
import io.vidocq.tools.dashboard.forge.ForgeRun;
import io.vidocq.tools.dashboard.forge.ForgeRunner;
import io.vidocq.tools.dashboard.forge.StatusContext;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.annotation.JsonbProperty;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The JSON of the Forgejo API, as raw records, and its mapping to the port's DTO. Dates are read as text: Forgejo
 * writes them with an offset ({@code +01:00}) and {@code 0001-01-01T00:00:00Z} for "not yet".
 */
public final class ForgejoJson {

    private static final Jsonb JSONB = JsonbBuilder.create();

    private ForgejoJson() {
    }

    public record RawUser(String login) {
    }

    public record RawRepo(long id, RawUser owner, String name,
                          @JsonbProperty("default_branch") String defaultBranch, boolean archived, boolean empty) {
    }

    public record RawRunner(long id, String uuid, String name, String status, List<String> labels, String version) {
    }

    public record RawStep(String status, String started, String stopped) {
    }

    public record RawJob(long id, @JsonbProperty("run_id") long runId, @JsonbProperty("repo_id") long repoId,
                         @JsonbProperty("task_id") long taskId, String name, String status,
                         @JsonbProperty("runs_on") List<String> runsOn, @JsonbProperty("html_url") String htmlUrl,
                         List<RawStep> steps) {
    }

    public record RawRun(long id, String event, String prettyref, @JsonbProperty("commit_sha") String commitSha,
                         String title, String status, String started, String stopped,
                         @JsonbProperty("html_url") String htmlUrl) {
    }

    public record RawBranchRef(String ref, String sha) {
    }

    public record RawPull(int number, String title, RawUser user, RawBranchRef head,
                          @JsonbProperty("html_url") String htmlUrl, @JsonbProperty("created_at") String createdAt,
                          @JsonbProperty("updated_at") String updatedAt, @JsonbProperty("merged_at") String mergedAt,
                          boolean draft) {
    }

    public record RawStatus(String context, String status, String state,
                            @JsonbProperty("target_url") String targetUrl) {
    }

    public record RawCombinedStatus(String state, List<RawStatus> statuses) {
    }

    public record RawGitPerson(String name, String email, String date) {
    }

    public record RawGitCommit(RawGitPerson author, RawGitPerson committer, String message) {
    }

    public record RawStats(int additions, int deletions) {
    }

    public record RawChangedFile(String filename) {
    }

    public record RawCommitItem(String sha, RawGitCommit commit, RawUser author, RawStats stats) {
    }

    public static List<ForgeRepo> toRepos(String json) {
        return list(json, RawRepo[].class).stream()
                .map(r -> new ForgeRepo(r.id(), login(r.owner()), r.name(), r.defaultBranch(), r.archived(), r.empty()))
                .toList();
    }

    public static List<ForgeRunner> toRunners(String json) {
        return list(json, RawRunner[].class).stream()
                .map(r -> new ForgeRunner(r.id(), r.uuid(), r.name(), r.status(), r.labels(), r.version()))
                .toList();
    }

    /** A list of jobs; {@code null} (an organisation with no active job) is an empty list. */
    public static List<ForgeJob> toJobs(String json) {
        return list(json, RawJob[].class).stream().map(ForgejoJson::job).toList();
    }

    public static ForgeJob toJob(String json) {
        return job(one(json, RawJob.class));
    }

    public static ForgeRun toRun(String json) {
        var r = one(json, RawRun.class);
        return new ForgeRun(r.id(), r.event(), r.prettyref(), r.commitSha(), r.title(), r.status(),
                instant(r.started()), instant(r.stopped()), r.htmlUrl());
    }

    public static List<ForgePull> toPulls(String json) {
        return list(json, RawPull[].class).stream().map(ForgejoJson::pull).toList();
    }

    /** The paths of a page of a pull request's changed files. */
    public static List<String> toFileNames(String json) {
        return list(json, RawChangedFile[].class).stream().map(RawChangedFile::filename)
                .filter(f -> f != null && !f.isBlank()).toList();
    }

    public static ForgePull toPull(String json) {
        return pull(one(json, RawPull.class));
    }

    /** {@code forgeUrl}: the forge, on which the targets of its Actions statuses are relative. */
    public static CombinedStatus toCombinedStatus(String json, String forgeUrl) {
        var s = one(json, RawCombinedStatus.class);
        var contexts = s.statuses() == null ? List.<StatusContext>of() : s.statuses().stream()
                .map(c -> new StatusContext(c.context(),
                        CiState.fromForgejo(c.status() != null ? c.status() : c.state()),
                        onForge(c.targetUrl(), forgeUrl)))
                .toList();
        return new CombinedStatus(CiState.fromForgejo(s.state()), contexts);
    }

    /**
     * Forgejo gives the target of an Actions status relative to itself ("/Vidocq/foy/actions/runs/933/jobs/1"), which
     * a browser would open on the dashboard's host: resolved on the forge. An absolute one (Sonar…) stays.
     */
    private static String onForge(String target, String forgeUrl) {
        if (target == null || target.isBlank()) {
            return null;
        }
        try {
            return URI.create(forgeUrl).resolve(target).toString();
        } catch (IllegalArgumentException e) {
            return target;
        }
    }

    public static List<RawCommit> toCommits(String json, String org, String repo) {
        return toCommitsSince(json, org, repo, Instant.MIN);
    }

    /**
     * The commits committed at or after {@code since}. Forgejo 17 ignores {@code ?since=} on {@code /commits}, and a
     * rebased commit keeps its old author date: the filter is on the committer date.
     */
    public static List<RawCommit> toCommitsSince(String json, String org, String repo, Instant since) {
        return list(json, RawCommitItem[].class).stream().filter(c -> {
            var git = c.commit();
            var when = git == null ? null : instant((git.committer() != null ? git.committer() : git.author()).date());
            return when == null || !when.isBefore(since);
        }).map(c -> {
            var git = c.commit();
            var person = git == null ? null : git.author();
            return new RawCommit(c.sha(), org, repo,
                    person == null ? null : instant(person.date()),
                    person == null ? null : person.name(),
                    person == null ? null : person.email(),
                    c.author() == null ? null : blankToNull(c.author().login()),
                    git == null ? "" : Objects.requireNonNullElse(git.message(), ""),
                    c.stats() == null ? 0 : c.stats().additions(),
                    c.stats() == null ? 0 : c.stats().deletions());
        }).toList();
    }

    private static ForgeJob job(RawJob j) {
        Instant start = null;
        Instant stop = null;
        boolean allStopped = j.steps() != null && !j.steps().isEmpty();
        for (RawStep step : j.steps() == null ? List.<RawStep>of() : j.steps()) {
            Instant s = instant(step.started());
            Instant e = instant(step.stopped());
            if (s != null && (start == null || s.isBefore(start))) {
                start = s;
            }
            if (e == null) {
                allStopped = false;
            } else if (stop == null || e.isAfter(stop)) {
                stop = e;
            }
        }
        return new ForgeJob(j.id(), j.runId(), j.repoId(), j.taskId(), j.name(), j.status(), j.runsOn(),
                j.htmlUrl(), start, allStopped ? stop : null);
    }

    private static ForgePull pull(RawPull p) {
        return new ForgePull(p.number(), p.title(), login(p.user()),
                p.head() == null ? null : p.head().ref(), p.head() == null ? null : p.head().sha(),
                p.htmlUrl(), instant(p.createdAt()), instant(p.updatedAt()), instant(p.mergedAt()), p.draft());
    }

    private static <T> List<T> list(String json, Class<T[]> type) {
        if (json == null || json.isBlank() || json.strip().equals("null")) {
            return List.of();
        }
        T[] items = JSONB.fromJson(json, type);
        return items == null ? List.of() : Arrays.stream(items).filter(Objects::nonNull).toList();
    }

    private static <T> T one(String json, Class<T> type) {
        return JSONB.fromJson(json, type);
    }

    private static String login(RawUser user) {
        return user == null ? null : blankToNull(user.login());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** An instant, or {@code null} for no value and for Forgejo's zero time. */
    static Instant instant(String text) {
        if (text == null || text.isBlank() || text.startsWith("0001-01-01")) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
