package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.sonar.SonarApi;
import io.vidocq.tools.dashboard.sonar.SonarKeys;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;
import io.vidocq.tools.dashboard.store.RepoRow;
import io.vidocq.tools.dashboard.store.RepoRowRepository;
import io.vidocq.tools.dashboard.store.SonarSnapshotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Sonar loop (15 min): the measures and gate of each repository that has a Sonar key, and the gate of each of its
 * open pull requests (those of the current snapshot). Stored, then set on the snapshot. Never calls Sonar when it is
 * disabled.
 */
@ApplicationScoped
public class SonarLoop {

    private final SonarApi sonar;
    private final RepoRowRepository repos;
    private final SonarSnapshotRepository snapshots;
    private final LiveState live;
    private final Clock clock;

    @Inject
    public SonarLoop(SonarApi sonar, RepoRowRepository repos, SonarSnapshotRepository snapshots, LiveState live,
                     Clock clock) {
        this.sonar = sonar;
        this.repos = repos;
        this.snapshots = snapshots;
        this.live = live;
        this.clock = clock;
    }

    public void tick(String org) {
        if (!sonar.enabled()) {
            return;
        }
        Instant now = clock.instant();
        OrgSnapshot current = live.snapshot(org);
        Map<String, SonarMeasures> measures = new HashMap<>();
        Map<String, GateStatus> pullGates = new HashMap<>();
        for (RepoRow row : repos.findByOrgOrderByNameAsc(org)) {
            if (Boolean.TRUE.equals(row.getArchived())) {
                continue;
            }
            String repo = row.getName();
            Optional<String> key = SonarKeys.keyFor(org, repo, Optional.ofNullable(row.getSonarKeyOverride()));
            if (key.isEmpty()) {
                continue;
            }
            projectMeasures(key.get()).ifPresent(m -> {
                measures.put(repo, m);
                snapshots.save(SonarRows.project(key.get(), org, repo, m, now));
            });
            for (PullView p : pullsOf(current, repo)) {
                sonar.pullRequestGate(key.get(), p.number()).ifPresent(g -> {
                    pullGates.put(repo + "#" + p.number(), g);
                    snapshots.save(SonarRows.pull(key.get(), org, repo, p.number(), g, now));
                });
            }
        }
        live.update(org, s -> s.withRepos(apply(s.repos(), measures, pullGates), now),
                new ChangeEvent.SonarChanged(org));
    }

    /** The measures, whose alert_status is the gate; the gate alone only when the measures lack it. */
    private Optional<SonarMeasures> projectMeasures(String key) {
        return sonar.measures(key).map(m -> m.gate() != GateStatus.NONE ? m
                : sonar.gate(key).map(g -> new SonarMeasures(m.ncloc(), m.tests(), m.coverage(), m.testFailures(),
                        m.testErrors(), m.bugs(), m.vulnerabilities(), m.codeSmells(), m.duplicatedLinesDensity(), g))
                        .orElse(m));
    }

    private static List<PullView> pullsOf(OrgSnapshot snapshot, String repo) {
        return snapshot.repos().stream().filter(r -> r.name().equals(repo)).findFirst()
                .map(RepoView::pulls).orElse(List.of());
    }

    /** The fresh measures and gates on the current repositories; what was not measured now keeps its old value. */
    private static List<RepoView> apply(List<RepoView> repos, Map<String, SonarMeasures> measures,
                                        Map<String, GateStatus> pullGates) {
        List<RepoView> out = new ArrayList<>();
        for (RepoView r : repos) {
            List<PullView> pulls = r.pulls().stream()
                    .map(p -> p.withSonarGate(pullGates.getOrDefault(r.name() + "#" + p.number(), p.sonarGate())))
                    .toList();
            out.add(new RepoView(r.name(), r.mainState(), r.mainSha(), r.lastCommitTitle(), r.lastCommitAt(),
                    measures.getOrDefault(r.name(), r.sonar()), pulls, r.htmlUrl()));
        }
        return out;
    }
}
