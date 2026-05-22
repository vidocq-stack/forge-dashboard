package io.vidocq.tools.dashboard.forgejo;

import io.vidocq.tools.dashboard.model.CommitInfo;
import io.vidocq.tools.dashboard.model.RepoInfo;
import io.vidocq.tools.dashboard.model.RunnerInfo;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Client minimal pour l'API Forgejo (compatible Gitea v1).
 *
 * <p>Squelette M0 : configuration via env (FORGEJO_URL, FORGEJO_TOKEN), HTTP via {@code java.net.http}
 * sur des virtual threads. Les methodes {@code listRepos}, {@code listRunners}, etc. sont a implementer.
 */
@ApplicationScoped
public class ForgejoClient {

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
    }

    private static String requireEnv(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variable d'environnement requise manquante : " + name);
        }
        return value;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "token " + token)
                .header("Accept", "application/json");
    }

    /** TODO M0 — GET /api/v1/orgs/{org}/repos puis enrichir avec topics, dernier commit, PRs ouvertes. */
    public List<RepoInfo> listRepos(String org) {
        throw new UnsupportedOperationException("a implementer en M0");
    }

    /** TODO M0 — GET /api/v1/admin/actions/runners. */
    public List<RunnerInfo> listRunners() {
        throw new UnsupportedOperationException("a implementer en M0");
    }

    /** TODO M0 — GET /api/v1/repos/{owner}/{repo}/commits?limit=1. */
    public CommitInfo getLatestCommit(String owner, String repo) {
        throw new UnsupportedOperationException("a implementer en M0");
    }

    HttpClient httpClient() { return http; }
    URI baseUri() { return baseUri; }
    HttpResponse<String> exchange(HttpRequest req) throws Exception {
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
