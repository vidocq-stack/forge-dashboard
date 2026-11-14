package io.vidocq.tools.dashboard.forge;

import io.vidocq.tools.dashboard.domain.RawCommit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read access to the forge (CodeFloe, Forgejo 17). Every method may throw {@link RateLimitedException} (429),
 * {@link ForgeNotFoundException} (404, or 409 for an empty repository) or {@link ForgeUnavailableException}
 * (any other failure).
 */
public interface ForgeApi {

    /** Every repository of the organisation, all pages. */
    List<ForgeRepo> listRepos(String org);

    /** The organisation's runners. */
    List<ForgeRunner> listRunners(String org);

    /** The organisation's waiting and running jobs; empty when the API answers {@code null}. */
    List<ForgeJob> listActiveJobs(String org);

    ForgeRun getRun(String owner, String repo, long runId);

    ForgeJob getJob(String owner, String repo, long jobId);

    /**
     * The first 4 KB of the job's log, read from the web route {@code <htmlUrl>/logs}, the API having none; empty when
     * the log does not exist yet or is empty.
     */
    Optional<String> getJobLogHead(ForgeJob job);

    List<ForgePull> listOpenPulls(String owner, String repo);

    /** Pull requests in any state updated since {@code since}, most recently updated first. */
    List<ForgePull> listPullsUpdatedSince(String owner, String repo, Instant since);

    CombinedStatus getCombinedStatus(String owner, String repo, String ref);

    /** One page (50 commits, with stats) of the branch's commits since {@code since}; empty past the last page. */
    List<RawCommit> listCommits(String owner, String repo, String branch, Instant since, int page);

    /** The newest commit of the branch, without stats (one light call); empty for an empty repository. */
    Optional<RawCommit> latestCommit(String owner, String repo, String branch);

    /** The login of the author of the pull request that brought {@code sha}, if any. */
    Optional<String> pullAuthorForCommit(String owner, String repo, String sha);

    /** Whether the token may read the organisation's runners. */
    boolean canReadRunners(String org);
}
