package io.vidocq.tools.dashboard.forge;

import io.vidocq.tools.dashboard.domain.RawCommit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory forge for the tests. The maps are filled by the tests; a key absent from a map is a 404
 * ({@link ForgeNotFoundException}) for single objects and an empty list for lists. {@link #failNext} makes the next call
 * of one method throw.
 */
public class FakeForgeApi implements ForgeApi {

    /** org → repos. */
    public final Map<String, List<ForgeRepo>> repos = new HashMap<>();
    /** org → runners. */
    public final Map<String, List<ForgeRunner>> runners = new HashMap<>();
    /** org → waiting and running jobs. */
    public final Map<String, List<ForgeJob>> activeJobs = new HashMap<>();
    /** run id → run. */
    public final Map<Long, ForgeRun> runs = new HashMap<>();
    /** job id → job. */
    public final Map<Long, ForgeJob> jobs = new HashMap<>();
    /** job id → log head. */
    public final Map<Long, String> logHeads = new HashMap<>();
    /** "owner/repo" → pulls, open or not. */
    public final Map<String, List<ForgePull>> pulls = new HashMap<>();
    /** "owner/repo#number" → paths of the changed files. */
    public final Map<String, List<String>> pullFiles = new HashMap<>();
    /** "owner/repo@ref:file" → content. */
    public final Map<String, String> files = new HashMap<>();
    /** "owner/repo@ref" → combined status. */
    public final Map<String, CombinedStatus> statuses = new HashMap<>();
    /** "owner/repo" → commits, newest first; paged by 50. */
    public final Map<String, List<RawCommit>> commits = new HashMap<>();
    /** "owner/repo@sha" → pull author login. */
    public final Map<String, String> pullAuthors = new HashMap<>();
    /** orgs whose runners the token may not read. */
    public final List<String> runnersForbidden = new ArrayList<>();
    /** Method name → number of calls. */
    public final Map<String, Integer> calls = new ConcurrentHashMap<>();
    /** The {@code since} of each {@link #listCommits} call, in order. */
    public final List<Instant> commitSinces = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();
    private final Map<String, RuntimeException> repoFailures = new ConcurrentHashMap<>();

    /** The next call of {@code method} (a {@link ForgeApi} method name) throws {@code e}. */
    public FakeForgeApi failNext(String method, RuntimeException e) {
        failures.put(method, e);
        return this;
    }

    /** Every call of {@code method} for {@code owner/repo} throws {@code e}, until {@link #recover}. */
    public FakeForgeApi failAlways(String method, String ownerRepo, RuntimeException e) {
        repoFailures.put(method + " " + ownerRepo, e);
        return this;
    }

    public FakeForgeApi recover() {
        repoFailures.clear();
        return this;
    }

    private void enter(String method) {
        calls.merge(method, 1, Integer::sum);
        RuntimeException e = failures.remove(method);
        if (e != null) {
            throw e;
        }
    }

    private void enter(String method, String owner, String repo) {
        enter(method);
        RuntimeException e = repoFailures.get(method + " " + owner + "/" + repo);
        if (e != null) {
            throw e;
        }
    }

    private static <T> T found(T value, String what) {
        if (value == null) {
            throw new ForgeNotFoundException(what);
        }
        return value;
    }

    @Override
    public List<ForgeRepo> listRepos(String org) {
        enter("listRepos");
        return List.copyOf(found(repos.get(org), "org " + org));
    }

    @Override
    public List<ForgeRunner> listRunners(String org) {
        enter("listRunners");
        return List.copyOf(runners.getOrDefault(org, List.of()));
    }

    @Override
    public List<ForgeJob> listActiveJobs(String org) {
        enter("listActiveJobs");
        return List.copyOf(activeJobs.getOrDefault(org, List.of()));
    }

    @Override
    public ForgeRun getRun(String owner, String repo, long runId) {
        enter("getRun", owner, repo);
        return found(runs.get(runId), "run " + runId);
    }

    @Override
    public ForgeJob getJob(String owner, String repo, long jobId) {
        enter("getJob", owner, repo);
        return found(jobs.get(jobId), "job " + jobId);
    }

    @Override
    public Optional<String> getJobLogHead(ForgeJob job) {
        enter("getJobLogHead");
        return Optional.ofNullable(logHeads.get(job.id()));
    }

    @Override
    public List<ForgePull> listOpenPulls(String owner, String repo) {
        enter("listOpenPulls", owner, repo);
        return pulls.getOrDefault(owner + "/" + repo, List.of()).stream().filter(p -> p.mergedAt() == null).toList();
    }

    @Override
    public List<ForgePull> listPullsUpdatedSince(String owner, String repo, Instant since) {
        enter("listPullsUpdatedSince", owner, repo);
        return pulls.getOrDefault(owner + "/" + repo, List.of()).stream()
                .filter(p -> p.updatedAt() == null || !p.updatedAt().isBefore(since)).toList();
    }

    @Override
    public List<String> listPullFiles(String owner, String repo, int number) {
        enter("listPullFiles", owner, repo);
        return List.copyOf(pullFiles.getOrDefault(owner + "/" + repo + "#" + number, List.of()));
    }

    @Override
    public Optional<String> readFile(String owner, String repo, String ref, String file) {
        enter("readFile", owner, repo);
        return Optional.ofNullable(files.get(owner + "/" + repo + "@" + ref + ":" + file));
    }

    @Override
    public CombinedStatus getCombinedStatus(String owner, String repo, String ref) {
        enter("getCombinedStatus", owner, repo);
        return found(statuses.get(owner + "/" + repo + "@" + ref), "status of " + ref);
    }

    @Override
    public List<RawCommit> listCommits(String owner, String repo, String branch, Instant since, int page) {
        enter("listCommits", owner, repo);
        commitSinces.add(since);
        List<RawCommit> all = found(commits.get(owner + "/" + repo), "repo " + owner + "/" + repo).stream()
                .filter(c -> c.authoredAt() == null || !c.authoredAt().isBefore(since)).toList();
        int from = (page - 1) * 50;
        return from >= all.size() ? List.of() : List.copyOf(all.subList(from, Math.min(all.size(), from + 50)));
    }

    @Override
    public Optional<RawCommit> latestCommit(String owner, String repo, String branch) {
        enter("latestCommit", owner, repo);
        return Optional.ofNullable(commits.get(owner + "/" + repo)).flatMap(all -> all.stream().findFirst());
    }

    @Override
    public Optional<String> pullAuthorForCommit(String owner, String repo, String sha) {
        enter("pullAuthorForCommit");
        return Optional.ofNullable(pullAuthors.get(owner + "/" + repo + "@" + sha));
    }

    @Override
    public boolean canReadRunners(String org) {
        enter("canReadRunners");
        return !runnersForbidden.contains(org);
    }
}
