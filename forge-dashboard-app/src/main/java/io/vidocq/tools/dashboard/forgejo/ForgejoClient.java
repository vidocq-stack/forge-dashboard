package io.vidocq.tools.dashboard.forgejo;

import io.vidocq.tools.dashboard.model.CommitInfo;
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

    /** GET /api/v1/orgs/{org}/repos (paginated). Topics et dernier commit sont enrichis par appels separes. */
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

    /** GET /api/v1/repos/{owner}/{repo}/commits?limit=1. */
    public Optional<CommitInfo> getLatestCommit(String owner, String repo) {
        var path = "/api/v1/repos/" + url(owner) + "/" + url(repo)
                + "/commits?limit=1&stat=false&verification=false&files=false";
        try {
            var arr = getArray(path);
            if (arr.isEmpty()) return Optional.empty();
            return Optional.of(toCommitInfo(arr.getJsonObject(0)));
        } catch (ForgejoException e) {
            // repo vide -> 409 ; on absorbe
            if (e.status() == 409 || e.status() == 404) return Optional.empty();
            throw e;
        }
    }

    /** Enrichit un RepoInfo light avec topics + dernier commit. */
    public RepoInfo enrich(RepoInfo light) {
        var topics = safeTopics(light.owner(), light.name());
        var lastCommit = getLatestCommit(light.owner(), light.name()).orElse(null);
        var specs = SpecsMapper.humanize(topics);
        return new RepoInfo(
                light.owner(), light.name(), light.fullName(), light.description(),
                light.archived(), light.priv(), light.htmlUrl(),
                topics, specs, light.updatedAt(), lastCommit,
                light.openPullRequests(), light.latestBuild());
    }

    private List<String> safeTopics(String owner, String repo) {
        try { return getTopics(owner, repo); }
        catch (ForgejoException e) { return List.of(); }
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
                List.of(),
                List.of(),
                parseInstant(r.getString("updated_at", null)),
                null,
                r.getInt("open_pr_counter", 0),
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
