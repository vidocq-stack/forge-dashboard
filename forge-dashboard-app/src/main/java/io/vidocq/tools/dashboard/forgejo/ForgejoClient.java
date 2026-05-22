package io.vidocq.tools.dashboard.forgejo;

import io.vidocq.tools.dashboard.model.BuildStatus;
import io.vidocq.tools.dashboard.model.CommitInfo;
import io.vidocq.tools.dashboard.model.PullRequestInfo;
import io.vidocq.tools.dashboard.model.RepoInfo;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Client minimal pour l'API Forgejo (compatible Gitea v1).
 * Parsing JSON via JSON-P (zero reflection). HTTP sur virtual threads.
 */
@ApplicationScoped
public class ForgejoClient {

    private static final Logger LOG = Logger.getLogger(ForgejoClient.class.getName());
    private static final int PAGE_LIMIT = 50;

    private HttpClient http;
    private URI baseUri;
    private String token;

    @PostConstruct
    void init() {
        var url = requireEnv("FORGEJO_URL");
        this.token = requireEnv("FORGEJO_TOKEN");
        this.baseUri = URI.create(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
        this.http = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        LOG.info(() -> "ForgejoClient pret : " + baseUri);
    }

    private static String requireEnv(String name) {
        var v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Variable d'environnement requise manquante : " + name);
        }
        return v;
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder()
                .uri(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "token " + token)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private String get(String path) {
        try {
            var resp = http.send(request(path), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new ForgejoException(resp.statusCode(), path, resp.body());
            }
            return resp.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ForgejoException(-1, path, e.getMessage());
        } catch (java.io.IOException e) {
            throw new ForgejoException(-1, path, e.getMessage());
        }
    }

    private JsonArray getArray(String path) {
        try (JsonReader r = Json.createReader(new StringReader(get(path)))) {
            return r.readArray();
        }
    }

    private JsonObject getObject(String path) {
        try (JsonReader r = Json.createReader(new StringReader(get(path)))) {
            return r.readObject();
        }
    }

    /** GET /api/v1/orgs/{org}/repos (paginated). */
    public List<RepoInfo> listRepos(String org) {
        var out = new ArrayList<RepoInfo>();
        int page = 1;
        while (true) {
            var path = "/api/v1/orgs/" + url(org) + "/repos?limit=" + PAGE_LIMIT + "&page=" + page;
            JsonArray arr = getArray(path);
            for (JsonValue v : arr) {
                out.add(toRepoInfoLight(v.asJsonObject()));
            }
            if (arr.size() < PAGE_LIMIT) break;
            page++;
        }
        return out;
    }

    /** GET /api/v1/repos/{owner}/{repo}/topics. */
    public List<String> getTopics(String owner, String repo) {
        var obj = getObject("/api/v1/repos/" + url(owner) + "/" + url(repo) + "/topics");
        if (!obj.containsKey("topics") || obj.isNull("topics")) return List.of();
        return obj.getJsonArray("topics").stream()
                .map(v -> ((jakarta.json.JsonString) v).getString())
                .toList();
    }

    /** GET /api/v1/repos/{owner}/{repo}/commits?limit=1&sha={branch}. */
    public Optional<CommitInfo> getLatestCommit(String owner, String repo, String branch) {
        var path = "/api/v1/repos/" + url(owner) + "/" + url(repo)
                + "/commits?limit=1&stat=false&verification=false&files=false"
                + (branch == null || branch.isBlank() ? "" : "&sha=" + url(branch));
        try {
            var arr = getArray(path);
            if (arr.isEmpty()) return Optional.empty();
            return Optional.of(toCommitInfo(arr.getJsonObject(0)));
        } catch (ForgejoException e) {
            if (e.status() == 409 || e.status() == 404) return Optional.empty();
            throw e;
        }
    }

    /** GET /api/v1/repos/{owner}/{repo}/commits/{ref}/status. */
    public BuildStatus getCommitStatus(String owner, String repo, String ref) {
        var path = "/api/v1/repos/" + url(owner) + "/" + url(repo)
                + "/commits/" + url(ref) + "/status";
        try {
            var obj = getObject(path);
            return toBuildStatus(obj);
        } catch (ForgejoException e) {
            if (e.status() == 404 || e.status() == 409 || e.status() == 422) return BuildStatus.NONE;
            throw e;
        }
    }

    /**
     * Derniere PR ouverte du repo. Retourne {@link Optional#empty()} si aucune.
     * Le build status du head.sha est resolu en meme temps.
     */
    public Optional<PullRequestInfo> getLatestOpenPr(String owner, String repo) {
        var path = "/api/v1/repos/" + url(owner) + "/" + url(repo)
                + "/pulls?state=open&sort=newest&limit=1";
        try {
            var arr = getArray(path);
            if (arr.isEmpty()) return Optional.empty();
            var pr = arr.getJsonObject(0);
            var head = pr.getJsonObject("head");
            var headSha = head == null ? "" : head.getString("sha", "");
            var build = headSha.isEmpty()
                    ? BuildStatus.NONE
                    : safeStatus(owner, repo, headSha);
            return Optional.of(new PullRequestInfo(
                    pr.getInt("number", 0),
                    pr.getString("title", ""),
                    headSha,
                    pr.getString("html_url", ""),
                    build));
        } catch (ForgejoException e) {
            if (e.status() == 404) return Optional.empty();
            throw e;
        }
    }

    /** Enrichit un RepoInfo light avec topics, dernier commit, main build status et derniere PR. */
    public RepoInfo enrich(RepoInfo light) {
        var topics = safeTopics(light.owner(), light.name());
        var defaultBranch = light.defaultBranch();
        var lastCommit = getLatestCommit(light.owner(), light.name(), defaultBranch).orElse(null);
        var mainBuild = safeStatus(light.owner(), light.name(), defaultBranch);
        var latestPr = safeLatestPr(light.owner(), light.name());
        var specs = SpecsMapper.humanize(topics);
        return new RepoInfo(
                light.owner(), light.name(), light.fullName(), light.description(),
                light.archived(), light.priv(), light.htmlUrl(), defaultBranch,
                topics, specs, light.updatedAt(), lastCommit,
                light.openPullRequests(), mainBuild, latestPr.orElse(null));
    }

    private List<String> safeTopics(String owner, String repo) {
        try { return getTopics(owner, repo); }
        catch (ForgejoException e) { return List.of(); }
    }

    private BuildStatus safeStatus(String owner, String repo, String ref) {
        try { return getCommitStatus(owner, repo, ref); }
        catch (ForgejoException e) { return BuildStatus.NONE; }
    }

    private Optional<PullRequestInfo> safeLatestPr(String owner, String repo) {
        try { return getLatestOpenPr(owner, repo); }
        catch (ForgejoException e) { return Optional.empty(); }
    }

    private static RepoInfo toRepoInfoLight(JsonObject r) {
        var owner = r.getJsonObject("owner").getString("username");
        var name = r.getString("name");
        return new RepoInfo(
                owner,
                name,
                r.getString("full_name", owner + "/" + name),
                r.getString("description", ""),
                r.getBoolean("archived", false),
                r.getBoolean("private", false),
                r.getString("html_url", ""),
                r.getString("default_branch", "main"),
                List.of(),
                List.of(),
                parseInstant(r.getString("updated_at", null)),
                null,
                r.getInt("open_pr_counter", 0),
                BuildStatus.NONE,
                null);
    }

    private static CommitInfo toCommitInfo(JsonObject c) {
        var sha = c.getString("sha");
        var html = c.getString("html_url", "");
        var commit = c.getJsonObject("commit");
        var author = commit.getJsonObject("author");
        var msg = commit.getString("message", "").strip();
        var firstLine = msg.contains("\n") ? msg.substring(0, msg.indexOf('\n')) : msg;
        return new CommitInfo(
                sha,
                sha.substring(0, Math.min(7, sha.length())),
                firstLine,
                author == null ? "" : author.getString("name", ""),
                author == null ? null : parseInstant(author.getString("date", null)),
                html);
    }

    /** Mappe la reponse combined status Forgejo vers notre {@link BuildStatus}. */
    private BuildStatus toBuildStatus(JsonObject obj) {
        var state = BuildStatus.State.of(obj.getString("state", null));
        if (state == BuildStatus.State.NONE) return BuildStatus.NONE;
        var count = obj.getInt("total_count", 0);
        String summary = null;
        String href = null;
        var arr = obj.containsKey("statuses") && !obj.isNull("statuses")
                ? obj.getJsonArray("statuses") : null;
        if (arr != null && !arr.isEmpty()) {
            JsonObject pick = arr.getJsonObject(0);
            // Privilegier un status non-success si l'etat global n'est pas success
            for (JsonValue v : arr) {
                var o = v.asJsonObject();
                var s = BuildStatus.State.of(o.getString("status", null));
                if (s == state) { pick = o; break; }
            }
            summary = pick.getString("description", pick.getString("context", null));
            href = pick.getString("target_url", null);
            if (href != null && href.startsWith("/")) {
                href = baseUri + href;
            }
        }
        return new BuildStatus(state, count, summary, href);
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try { return OffsetDateTime.parse(s).toInstant(); }
        catch (Exception e) { return null; }
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** URL de base de la forge (utile pour construire des liens absolus dans l'UI). */
    public String baseUrl() { return baseUri.toString(); }

    public static final class ForgejoException extends RuntimeException {
        private final int status;
        public ForgejoException(int status, String path, String body) {
            super("Forgejo " + status + " sur " + path + " : " + truncate(body));
            this.status = status;
        }
        public int status() { return status; }
        private static String truncate(String s) {
            if (s == null) return "";
            return s.length() > 200 ? s.substring(0, 200) + "..." : s;
        }
    }
}
