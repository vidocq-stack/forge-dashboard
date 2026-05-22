package io.vidocq.tools.dashboard.service;

import io.vidocq.tools.dashboard.forgejo.ForgejoClient;
import io.vidocq.tools.dashboard.model.RepoInfo;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service d'agregation : interroge Forgejo pour les orgs configurees, enrichit chaque
 * repo (topics + dernier commit), met le resultat en cache memoire avec TTL.
 *
 * <p>Configuration via env :
 * <ul>
 *   <li>{@code FORGEJO_ORGS} — CSV des orgs a scanner (defaut : "vidocq,vidocq-tools")</li>
 *   <li>{@code DASHBOARD_CACHE_TTL_SECONDS} — TTL du cache (defaut : 30)</li>
 * </ul>
 */
@ApplicationScoped
public class DashboardService {

    private static final Logger LOG = Logger.getLogger(DashboardService.class.getName());

    @Inject
    ForgejoClient forgejo;

    private List<String> orgs;
    private Duration ttl;
    private final AtomicReference<Snapshot> cache = new AtomicReference<>(Snapshot.empty());

    @PostConstruct
    void init() {
        var orgsEnv = System.getenv().getOrDefault("FORGEJO_ORGS", "vidocq,vidocq-tools");
        this.orgs = Arrays.stream(orgsEnv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        var ttlSeconds = Integer.parseInt(
                System.getenv().getOrDefault("DASHBOARD_CACHE_TTL_SECONDS", "30"));
        this.ttl = Duration.ofSeconds(ttlSeconds);
        LOG.info(() -> "DashboardService : orgs=" + orgs + ", ttl=" + ttl);
    }

    /** Retourne le snapshot courant (refresh si expire). */
    public Snapshot getSnapshot() {
        var current = cache.get();
        if (current.isFresh(ttl)) return current;
        synchronized (cache) {
            current = cache.get();
            if (current.isFresh(ttl)) return current;
            var refreshed = refresh();
            cache.set(refreshed);
            return refreshed;
        }
    }

    private Snapshot refresh() {
        var t0 = System.currentTimeMillis();
        var allRepos = new ArrayList<RepoInfo>();
        String error = null;
        for (var org : orgs) {
            try {
                var light = forgejo.listRepos(org);
                for (var r : light) {
                    try {
                        allRepos.add(forgejo.enrich(r));
                    } catch (RuntimeException e) {
                        LOG.log(Level.WARNING, e, () -> "enrich(" + r.fullName() + ") echec");
                        allRepos.add(r);
                    }
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, e, () -> "listRepos(" + org + ") echec");
                error = (error == null ? "" : error + " ; ") + org + ": " + e.getMessage();
            }
        }
        allRepos.sort(Comparator.comparing(
                (RepoInfo r) -> r.updatedAt() == null ? Instant.EPOCH : r.updatedAt()).reversed());
        var elapsed = System.currentTimeMillis() - t0;
        LOG.info(() -> "Snapshot rafraichi : " + allRepos.size() + " repos en " + elapsed + "ms");
        return new Snapshot(Instant.now(), List.copyOf(allRepos), error);
    }

    public List<String> orgs() { return orgs; }

    /** Snapshot immuable du dashboard. */
    public record Snapshot(Instant takenAt, List<RepoInfo> repos, String error) {
        public static Snapshot empty() { return new Snapshot(Instant.EPOCH, List.of(), null); }
        public boolean isFresh(Duration ttl) {
            return takenAt != Instant.EPOCH
                    && Duration.between(takenAt, Instant.now()).compareTo(ttl) < 0;
        }
    }
}
