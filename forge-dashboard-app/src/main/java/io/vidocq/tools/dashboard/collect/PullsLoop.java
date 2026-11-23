package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.domain.ComponentMap;
import io.vidocq.tools.dashboard.domain.RawCommit;
import io.vidocq.tools.dashboard.forge.CombinedStatus;
import io.vidocq.tools.dashboard.forge.ForgeApi;
import io.vidocq.tools.dashboard.forge.ForgeLinks;
import io.vidocq.tools.dashboard.forge.ForgeNotFoundException;
import io.vidocq.tools.dashboard.forge.ForgePull;
import io.vidocq.tools.dashboard.forge.ForgeRepo;
import io.vidocq.tools.dashboard.forge.ForgeUnavailableException;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.store.PullRequestState;
import io.vidocq.tools.dashboard.store.PullRequestStateRepository;
import io.vidocq.tools.dashboard.store.RepoRow;
import io.vidocq.tools.dashboard.store.RepoRowRepository;
import io.vidocq.tools.dashboard.store.SonarSnapshotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The pull requests loop (90 s): for each repository of an organisation, the combined status of its default branch
 * and its last commit, its open pull requests and the combined status of their heads. The Sonar measures stay those of
 * the current snapshot, or of the database after a restart: {@link SonarLoop} owns them. One repository's failure
 * does not stop the others ({@link RepoPass}).
 */
@ApplicationScoped
public class PullsLoop {

    /** The file at the root of a monorepo that maps its components to their folders. */
    static final String COMPONENTS = "components.json";

    private final ForgeApi forge;
    private final RepoRowRepository repos;
    private final PullRequestStateRepository pullStates;
    private final SonarSnapshotRepository sonarSnapshots;
    private final LiveState live;
    private final ForgeLinks links;
    private final Clock clock;

    /** "org/repo" → its components.json, read for the main branch commit it carries. */
    private final Map<String, KnownMap> maps = new ConcurrentHashMap<>();
    /** "org/repo#n" → the changed files of the pull request, read for the head it carries. */
    private final Map<String, KnownFiles> files = new ConcurrentHashMap<>();

    private record KnownMap(String mainSha, ComponentMap map) {
    }

    private record KnownFiles(String headSha, List<String> paths) {
    }

    @Inject
    public PullsLoop(ForgeApi forge, RepoRowRepository repos, PullRequestStateRepository pullStates,
                     SonarSnapshotRepository sonarSnapshots, LiveState live, ForgeLinks links, Clock clock) {
        this.forge = forge;
        this.repos = repos;
        this.pullStates = pullStates;
        this.sonarSnapshots = sonarSnapshots;
        this.live = live;
        this.links = links;
        this.clock = clock;
    }

    /** Upserts the repositories of {@code org}, keeping the Sonar override and the commits cursor. */
    public void refreshRepos(String org) {
        for (ForgeRepo r : forge.listRepos(org)) {
            RepoRow row = repos.findById(r.id()).orElseGet(RepoRow::new);
            row.setId(r.id());
            row.setOrg(org);
            row.setName(r.name());
            row.setDefaultBranch(r.defaultBranch() == null || r.defaultBranch().isBlank() ? "main" : r.defaultBranch());
            row.setArchived(r.archived());
            row.setEmptyRepo(r.empty());
            repos.save(row);
        }
    }

    /**
     * At start, before any pass: the projects the database holds, with their open pull requests and Sonar measures,
     * so that a restart while CodeFloe is down shows the last state (marked stale) rather than nothing. The main
     * branch state is not stored: unknown until the first pass. A snapshot a pass already filled is left alone.
     */
    public void restore(String org) {
        Map<String, List<PullView>> pulls = new HashMap<>();
        for (PullRequestState s : pullStates.findByOrg(org)) {
            pulls.computeIfAbsent(s.getRepo(), r -> new ArrayList<>()).add(new PullView(s.getRepo(), s.getNumber(),
                    s.getTitle(), s.getAuthorLogin(), s.getHeadSha(), s.getCiState(), List.of(), null, s.getHtmlUrl(),
                    s.getUpdatedAt()));
        }
        List<RepoView> views = new ArrayList<>();
        for (RepoRow row : repos.findByOrgOrderByNameAsc(org)) {
            if (!Boolean.TRUE.equals(row.getArchived()) && !Boolean.TRUE.equals(row.getEmptyRepo())) {
                views.add(new RepoView(row.getName(), CiState.NONE, null, null, null, null,
                        pulls.getOrDefault(row.getName(), List.of()), links.repo(org, row.getName())));
            }
        }
        if (views.isEmpty()) {
            return;
        }
        SonarCarry carry = SonarCarry.of(org, sonarSnapshots);
        live.update(org, s -> s.repos().isEmpty() ? s.withRepos(carry.onto(s, views), s.updatedAt()) : s,
                new ChangeEvent.PullsChanged(org));
    }

    /** One pass for {@code org}; the repositories are read once if none is known yet. */
    public void tick(String org) {
        Instant now = clock.instant();
        List<RepoRow> rows = repos.findByOrgOrderByNameAsc(org);
        if (rows.isEmpty()) {
            refreshRepos(org);
            rows = repos.findByOrgOrderByNameAsc(org);
        }
        List<RepoView> views = new ArrayList<>();
        OrgSnapshot current = live.snapshot(org);
        RepoPass pass = new RepoPass(org);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<RepoView>> futures = new LinkedHashMap<>();
            for (RepoRow row : rows) {
                if (!Boolean.TRUE.equals(row.getArchived()) && !Boolean.TRUE.equals(row.getEmptyRepo())) {
                    futures.put(row.getName(), executor.submit(() -> repoView(org, row, now)));
                }
            }
            futures.forEach((name, f) -> {
                switch (pass.read(name, () -> get(f))) {
                    case RepoPass.Outcome.Read<RepoView> read -> views.add(read.value());
                    // CodeFloe cannot serve it now: its last view stays rather than a hole in the list
                    case RepoPass.Outcome.Unavailable<RepoView> _ -> current.repos().stream()
                            .filter(v -> v.name().equals(name)).findFirst().ifPresent(views::add);
                    case RepoPass.Outcome.Gone<RepoView> _ -> { }
                }
            });
        }
        pass.end();
        SonarCarry carry = SonarCarry.of(org, sonarSnapshots);
        live.update(org, s -> s.withRepos(carry.onto(s, views), now), new ChangeEvent.PullsChanged(org));
    }

    private RepoView repoView(String org, RepoRow row, Instant now) {
        String name = row.getName();
        CombinedStatus main = forge.getCombinedStatus(org, name, row.getDefaultBranch());
        Optional<RawCommit> last = forge.latestCommit(org, name, row.getDefaultBranch());
        ComponentMap map = componentMap(org, row, last.map(RawCommit::sha).orElse(null));
        List<PullView> pulls = new ArrayList<>();
        Set<String> open = new HashSet<>();
        for (ForgePull p : openPulls(org, name)) {
            CombinedStatus status = headStatus(org, name, p.headSha());
            pulls.add(new PullView(name, p.number(), p.title(), p.authorLogin(), p.headSha(), status.state(),
                    status.contexts(), null, p.htmlUrl(), p.updatedAt(), fileTags(org, name, p, map)));
            pullStates.save(state(org, name, p, status.state(), now));
            open.add(PullRequestState.idOf(org, name, p.number()));
        }
        files.keySet().removeIf(id -> id.startsWith(org + "/" + name + "#") && !open.contains(id));
        pullStates.findByOrg(org).stream()
                .filter(s -> name.equals(s.getRepo()) && !open.contains(s.getId()))
                .forEach(pullStates::delete);
        return new RepoView(name, main.state(), last.map(RawCommit::sha).orElse(null),
                last.map(c -> firstLine(c.message())).orElse(null), last.map(RawCommit::authoredAt).orElse(null),
                null, pulls, links.repo(org, name));
    }

    /**
     * The components.json at the root of the main branch, read again when the branch gets a new commit; none when
     * absent. Unreadable now (CodeFloe down): the last one read, or none, for this pass; read again on the next one.
     * A rate limit ends the pass, for the loop to wait as CodeFloe asks.
     */
    private ComponentMap componentMap(String org, RepoRow row, String mainSha) {
        String key = org + "/" + row.getName();
        KnownMap known = maps.get(key);
        if (known != null && mainSha != null && mainSha.equals(known.mainSha())) {
            return known.map();
        }
        try {
            ComponentMap map = forge.readFile(org, row.getName(), row.getDefaultBranch(), COMPONENTS)
                    .map(ComponentMap::parse).orElse(ComponentMap.EMPTY);
            maps.put(key, new KnownMap(mainSha, map));
            return map;
        } catch (ForgeUnavailableException | ForgeNotFoundException unreadable) {
            return known == null ? ComponentMap.EMPTY : known.map();
        }
    }

    /**
     * The components of the pull request's changed files, in a repository with a components.json; its files are read
     * again when its head changes. Unreadable now: the last ones read for this pass, or none (the title's tags stand
     * alone); read again on the next one.
     */
    private List<String> fileTags(String org, String repo, ForgePull p, ComponentMap map) {
        if (map.isEmpty()) {
            return List.of();
        }
        String id = PullRequestState.idOf(org, repo, p.number());
        KnownFiles known = files.get(id);
        if (known == null || !Objects.equals(known.headSha(), p.headSha())) {
            try {
                known = new KnownFiles(p.headSha(), forge.listPullFiles(org, repo, p.number()));
                files.put(id, known);
            } catch (ForgeUnavailableException | ForgeNotFoundException unreadable) {
                return known == null ? List.of() : map.tagsOf(known.paths());
            }
        }
        return map.tagsOf(known.paths());
    }

    /** Forgejo answers 404 on the pulls of a repository that has them disabled (a mirror, say): none. */
    private List<ForgePull> openPulls(String org, String repo) {
        try {
            return forge.listOpenPulls(org, repo);
        } catch (ForgeNotFoundException disabled) {
            return List.of();
        }
    }

    private CombinedStatus headStatus(String org, String repo, String sha) {
        try {
            return forge.getCombinedStatus(org, repo, sha);
        } catch (ForgeNotFoundException none) {
            return new CombinedStatus(CiState.NONE, List.of());
        }
    }

    private static PullRequestState state(String org, String repo, ForgePull p, CiState ci, Instant now) {
        var s = new PullRequestState();
        s.setId(PullRequestState.idOf(org, repo, p.number()));
        s.setOrg(org);
        s.setRepo(repo);
        s.setNumber(p.number());
        s.setTitle(p.title());
        s.setAuthorLogin(p.authorLogin());
        s.setHeadRef(p.headRef());
        s.setHeadSha(p.headSha());
        s.setHtmlUrl(p.htmlUrl());
        s.setDraft(p.draft());
        s.setCiState(ci);
        s.setCreatedAt(p.createdAt());
        s.setUpdatedAt(p.updatedAt());
        s.setObservedAt(now);
        return s;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return null;
        }
        int eol = message.indexOf('\n');
        return (eol < 0 ? message : message.substring(0, eol)).strip();
    }

    /** The repository's read, or the failure it ended with, for {@link RepoPass} to sort. */
    private static <T> T get(Future<T> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e.getCause());
        }
    }

    /** The Sonar measures and pull request gates a pull requests tick keeps: the snapshot's, else the stored ones. */
    record SonarCarry(SonarRows stored) {

        static SonarCarry of(String org, SonarSnapshotRepository snapshots) {
            return new SonarCarry(SonarRows.of(snapshots.findByOrg(org)));
        }

        List<RepoView> onto(OrgSnapshot current, List<RepoView> fresh) {
            List<RepoView> out = new ArrayList<>();
            for (RepoView r : fresh) {
                Optional<RepoView> old = current.repos().stream().filter(o -> o.name().equals(r.name())).findFirst();
                var sonar = old.map(RepoView::sonar).orElse(null);
                if (sonar == null) {
                    sonar = stored.measures(r.name());
                }
                List<PullView> pulls = new ArrayList<>();
                for (PullView p : r.pulls()) {
                    var gate = old.flatMap(o -> o.pulls().stream().filter(q -> q.number() == p.number()).findFirst())
                            .map(PullView::sonarGate).orElse(null);
                    if (gate == null) {
                        gate = stored.pullGate(r.name(), p.number());
                    }
                    pulls.add(p.withSonarGate(gate));
                }
                out.add(new RepoView(r.name(), r.mainState(), r.mainSha(), r.lastCommitTitle(), r.lastCommitAt(),
                        sonar, pulls, r.htmlUrl()));
            }
            return out;
        }
    }
}
