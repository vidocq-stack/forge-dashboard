package io.vidocq.tools.dashboard.forge.cyrano;

import io.vidocq.tools.dashboard.domain.RawCommit;
import io.vidocq.tools.dashboard.forge.CombinedStatus;
import io.vidocq.tools.dashboard.forge.ForgeApi;
import io.vidocq.tools.dashboard.forge.ForgeJob;
import io.vidocq.tools.dashboard.forge.ForgeNotFoundException;
import io.vidocq.tools.dashboard.forge.ForgePull;
import io.vidocq.tools.dashboard.forge.ForgeRepo;
import io.vidocq.tools.dashboard.forge.ForgeRun;
import io.vidocq.tools.dashboard.forge.ForgeRunner;
import io.vidocq.tools.dashboard.forge.ForgeUnavailableException;
import io.vidocq.tools.dashboard.forge.NextPage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** {@link ForgeApi} over the Forgejo API, through Cyrano. At most 4 calls at a time, served in arrival order. */
@ApplicationScoped
public class CyranoForgeApi implements ForgeApi {

    static final int LIMIT = 50;
    static final int LOG_HEAD_BYTES = 4096;
    private static final int MAX_PAGES = 200;

    private final Semaphore permits = new Semaphore(4, true);

    @Inject
    @RestClient
    ForgejoClient api;

    @Inject
    @RestClient
    ForgejoWebClient web;

    @Inject
    @ConfigProperty(name = "FORGEJO_URL", defaultValue = "https://codefloe.com")
    String forgeUrl;

    private record Answer(int status, String body, String link, String retryAfter) {
    }

    @Override
    public List<ForgeRepo> listRepos(String org) {
        return all("repos of " + org, page -> api.repos(org, page, LIMIT), ForgejoJson::toRepos);
    }

    @Override
    public List<ForgeRunner> listRunners(String org) {
        return ForgejoJson.toRunners(get("runners of " + org, () -> api.runners(org)).body());
    }

    @Override
    public List<ForgeJob> listActiveJobs(String org) {
        return ForgejoJson.toJobs(get("active jobs of " + org, () -> api.activeJobs(org)).body());
    }

    @Override
    public ForgeRun getRun(String owner, String repo, long runId) {
        return ForgejoJson.toRun(get("run " + runId + " of " + owner + "/" + repo,
                () -> api.run(owner, repo, runId)).body());
    }

    @Override
    public ForgeJob getJob(String owner, String repo, long jobId) {
        return ForgejoJson.toJob(get("job " + jobId + " of " + owner + "/" + repo,
                () -> api.job(owner, repo, jobId)).body());
    }

    @Override
    public Optional<String> getJobLogHead(ForgeJob job) {
        Optional<JobLogPath> path = JobLogPath.parse(job.htmlUrl(), forgeUrl);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        JobLogPath p = path.get();
        Answer answer = call("log of job " + job.id(),
                () -> web.log(p.owner(), p.repo(), p.run(), p.job(), p.attempt(), "bytes=0-" + (LOG_HEAD_BYTES - 1)));
        if (answer.status() == 404 || answer.status() == 416) {
            return Optional.empty();
        }
        ForgeErrors.check(answer.status(), answer.retryAfter(), "log of job " + job.id(), Instant.now());
        String body = answer.body();
        if (body.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(body.length() > LOG_HEAD_BYTES ? body.substring(0, LOG_HEAD_BYTES) : body);
    }

    @Override
    public List<ForgePull> listOpenPulls(String owner, String repo) {
        return all("open pulls of " + owner + "/" + repo,
                page -> api.pulls(owner, repo, "open", null, page, LIMIT), ForgejoJson::toPulls);
    }

    @Override
    public List<ForgePull> listPullsUpdatedSince(String owner, String repo, Instant since) {
        String what = "pulls of " + owner + "/" + repo;
        List<ForgePull> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            int p = page;
            Answer answer = get(what, () -> api.pulls(owner, repo, "all", "recentupdate", p, LIMIT));
            boolean older = false;
            for (ForgePull pull : ForgejoJson.toPulls(answer.body())) {
                if (pull.updatedAt() != null && pull.updatedAt().isBefore(since)) {
                    older = true;
                } else {
                    out.add(pull);
                }
            }
            if (older || !NextPage.hasNext(answer.link())) {
                break;
            }
        }
        return out;
    }

    @Override
    public CombinedStatus getCombinedStatus(String owner, String repo, String ref) {
        return ForgejoJson.toCombinedStatus(get("status of " + owner + "/" + repo + "@" + ref,
                () -> api.status(owner, repo, ref, LIMIT)).body());
    }

    @Override
    public List<RawCommit> listCommits(String owner, String repo, String branch, Instant since, int page) {
        Answer answer = get("commits of " + owner + "/" + repo,
                () -> api.commits(owner, repo, branch, true, false, false, page, LIMIT));
        return ForgejoJson.toCommitsSince(answer.body(), owner, repo, since);
    }

    @Override
    public Optional<RawCommit> latestCommit(String owner, String repo, String branch) {
        try {
            String body = get("last commit of " + owner + "/" + repo,
                    () -> api.commits(owner, repo, branch, false, false, false, 1, 1)).body();
            return ForgejoJson.toCommits(body, owner, repo).stream().findFirst();
        } catch (ForgeNotFoundException emptyRepository) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> pullAuthorForCommit(String owner, String repo, String sha) {
        try {
            String body = get("pull of " + owner + "/" + repo + "@" + sha,
                    () -> api.commitPull(owner, repo, sha)).body();
            return Optional.ofNullable(ForgejoJson.toPull(body).authorLogin());
        } catch (ForgeNotFoundException noPull) {
            return Optional.empty();
        }
    }

    @Override
    public boolean canReadRunners(String org) {
        Answer answer = call("runners of " + org, () -> api.runners(org));
        if (answer.status() == 401 || answer.status() == 403 || answer.status() == 404) {
            return false;
        }
        ForgeErrors.check(answer.status(), answer.retryAfter(), "runners of " + org, Instant.now());
        return true;
    }

    /** Every page, while the {@code Link} header announces a next one. */
    private <T> List<T> all(String what, IntFunction<Response> page, Function<String, List<T>> map) {
        List<T> out = new ArrayList<>();
        for (int n = 1; n <= MAX_PAGES; n++) {
            int p = n;
            Answer answer = get(what, () -> page.apply(p));
            out.addAll(map.apply(answer.body()));
            if (!NextPage.hasNext(answer.link())) {
                break;
            }
        }
        return out;
    }

    /** A 2xx answer, or the port's exception. */
    private Answer get(String what, Supplier<Response> request) {
        Answer answer = call(what, request);
        ForgeErrors.check(answer.status(), answer.retryAfter(), what, Instant.now());
        return answer;
    }

    /** Any answer, its status unchecked; a transport failure is a {@link ForgeUnavailableException}. */
    private Answer call(String what, Supplier<Response> request) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ForgeUnavailableException("interrupted while waiting to call CodeFloe for " + what, e);
        }
        try (Response response = send(what, request)) {
            String body = response.hasEntity() ? response.readEntity(String.class) : "";
            return new Answer(response.getStatus(), body == null ? "" : body, response.getHeaderString("Link"),
                    response.getHeaderString("Retry-After"));
        } finally {
            permits.release();
        }
    }

    /** The response, also when the default mapper turned an error status into an exception. */
    private static Response send(String what, Supplier<Response> request) {
        try {
            return request.get();
        } catch (WebApplicationException e) {
            return e.getResponse();
        } catch (RuntimeException e) {
            throw new ForgeUnavailableException("CodeFloe unreachable for " + what, e);
        }
    }
}
