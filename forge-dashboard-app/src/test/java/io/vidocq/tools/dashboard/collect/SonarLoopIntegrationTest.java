package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.FakeForgeApi;
import io.vidocq.tools.dashboard.forge.ForgeLinks;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.sonar.FakeSonarApi;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;
import io.vidocq.tools.dashboard.store.PullRequestStateRepository;
import io.vidocq.tools.dashboard.store.RepoRowRepository;
import io.vidocq.tools.dashboard.store.SonarSnapshotRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.testing.TestClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.vidocq.tools.dashboard.collect.PullsLoopIntegrationTest.ORG;
import static io.vidocq.tools.dashboard.collect.PullsLoopIntegrationTest.T0;
import static io.vidocq.tools.dashboard.collect.PullsLoopIntegrationTest.commit;
import static io.vidocq.tools.dashboard.collect.PullsLoopIntegrationTest.pull;
import static io.vidocq.tools.dashboard.collect.PullsLoopIntegrationTest.repo;
import static io.vidocq.tools.dashboard.collect.PullsLoopIntegrationTest.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Sonar loop, and how its measures live along the pull requests loop. */
class SonarLoopIntegrationTest {

    private static RepoRowRepository repos;
    private static PullRequestStateRepository pullStates;
    private static SonarSnapshotRepository snapshots;

    private FakeForgeApi forge;
    private FakeSonarApi sonar;
    private LiveState live;

    @BeforeAll
    static void startApplication() {
        repos = InProcessApp.bean(RepoRowRepository.class);
        pullStates = InProcessApp.bean(PullRequestStateRepository.class);
        snapshots = InProcessApp.bean(SonarSnapshotRepository.class);
    }

    @BeforeEach
    void reset() {
        InProcessApp.truncate("repo", "pull_request_state", "sonar_snapshot");
        forge = new FakeForgeApi();
        sonar = new FakeSonarApi();
        live = new LiveState(new ChangeBus());
        forge.repos.put(ORG, List.of(repo(1, "vauban", false)));
        forge.statuses.put("Vidocq/vauban@main", status(io.vidocq.tools.dashboard.domain.CiState.SUCCESS));
        forge.commits.put("Vidocq/vauban", List.of(commit("c0ffee", "feat: x")));
        forge.pulls.put("Vidocq/vauban", List.of(pull(298, "h298")));
        forge.statuses.put("Vidocq/vauban@h298", status(io.vidocq.tools.dashboard.domain.CiState.PENDING));
        sonar.measures.put("vidocq-vauban", measures(41_814L));
        sonar.pullRequestGates.put("vidocq-vauban#298", GateStatus.ERROR);
    }

    private static SonarMeasures measures(long ncloc) {
        return new SonarMeasures(ncloc, 2090L, 86.9, 0L, 0L, 0L, 0L, 3L, 0.8, GateStatus.OK);
    }

    private PullsLoop pulls() {
        return new PullsLoop(forge, repos, pullStates, snapshots, live, new ForgeLinks("https://codefloe.com"),
                new TestClock(T0));
    }

    private SonarLoop sonarLoop() {
        return new SonarLoop(sonar, repos, snapshots, live, new TestClock(T0));
    }

    private RepoView vauban() {
        return live.snapshot(ORG).repos().getFirst();
    }

    @Test
    void sonarMeasuresAndPrGateReachSnapshot() {
        pulls().tick(ORG);
        sonarLoop().tick(ORG);

        assertEquals(41_814L, vauban().sonar().ncloc());
        assertEquals(GateStatus.OK, vauban().sonar().gate());
        assertEquals(GateStatus.ERROR, vauban().pulls().getFirst().sonarGate());
        assertTrue(snapshots.findById("vidocq-vauban").isPresent());
        assertEquals(GateStatus.ERROR, snapshots.findById("vidocq-vauban#298").orElseThrow().getGate());
    }

    @Test
    void sonarSurvivesTheNextPullsTick() {
        var pulls = pulls();
        pulls.tick(ORG);
        sonarLoop().tick(ORG);
        pulls.tick(ORG);

        assertEquals(41_814L, vauban().sonar().ncloc());
        assertEquals(GateStatus.ERROR, vauban().pulls().getFirst().sonarGate());
    }

    @Test
    void storedMeasuresAreShownAfterARestart() {
        pulls().tick(ORG);
        sonarLoop().tick(ORG);

        live = new LiveState(new ChangeBus());   // restart: empty memory, the database keeps the measures
        pulls().tick(ORG);

        assertEquals(41_814L, vauban().sonar().ncloc());
        assertEquals(GateStatus.ERROR, vauban().pulls().getFirst().sonarGate());
    }

    @Test
    void sonarDisabledLeavesSonarNull() {
        sonar.enabled = false;
        pulls().tick(ORG);
        sonarLoop().tick(ORG);

        assertNull(vauban().sonar());
        assertTrue(sonar.calls.isEmpty());
    }

    @Test
    void repoWithoutKeyIsNotMeasured() {
        forge.repos.put("VidocqTools", List.of(new io.vidocq.tools.dashboard.forge.ForgeRepo(5, "VidocqTools",
                "arago", "main", false, false)));
        forge.statuses.put("VidocqTools/arago@main", status(io.vidocq.tools.dashboard.domain.CiState.SUCCESS));
        pulls().tick("VidocqTools");
        sonarLoop().tick("VidocqTools");

        assertNull(live.snapshot("VidocqTools").repos().getFirst().sonar());
        assertNull(sonar.calls.get("measures"));
    }
}
