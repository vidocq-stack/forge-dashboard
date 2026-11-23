package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.domain.RawCommit;
import io.vidocq.tools.dashboard.forge.CombinedStatus;
import io.vidocq.tools.dashboard.forge.FakeForgeApi;
import io.vidocq.tools.dashboard.forge.ForgeLinks;
import io.vidocq.tools.dashboard.forge.ForgeNotFoundException;
import io.vidocq.tools.dashboard.forge.ForgeUnavailableException;
import io.vidocq.tools.dashboard.forge.ForgePull;
import io.vidocq.tools.dashboard.forge.ForgeRepo;
import io.vidocq.tools.dashboard.forge.StatusContext;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.LiveState;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.store.PullRequestStateRepository;
import io.vidocq.tools.dashboard.store.RepoRow;
import io.vidocq.tools.dashboard.store.RepoRowRepository;
import io.vidocq.tools.dashboard.store.SonarSnapshotRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.testing.TestClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pull requests and main branch loop against a fake CodeFloe and the PostgreSQL of the dev services. */
class PullsLoopIntegrationTest {

    static final String ORG = "Vidocq";
    static final Instant T0 = Instant.parse("2026-11-14T12:00:00Z");

    private static RepoRowRepository repos;
    private static PullRequestStateRepository pullStates;
    private static SonarSnapshotRepository sonarSnapshots;

    private FakeForgeApi forge;
    private LiveState live;

    @BeforeAll
    static void startApplication() {
        repos = InProcessApp.bean(RepoRowRepository.class);
        pullStates = InProcessApp.bean(PullRequestStateRepository.class);
        sonarSnapshots = InProcessApp.bean(SonarSnapshotRepository.class);
    }

    @BeforeEach
    void reset() {
        InProcessApp.truncate("repo", "pull_request_state", "sonar_snapshot");
        forge = new FakeForgeApi();
        live = new LiveState(new ChangeBus());
        forge.repos.put(ORG, new ArrayList<>(List.of(repo(1, "vauban", false))));
        forge.statuses.put("Vidocq/vauban@main", status(CiState.SUCCESS));
        forge.commits.put("Vidocq/vauban", List.of(commit("c0ffee", "feat: last one\n\nbody")));
        forge.pulls.put("Vidocq/vauban", List.of(pull(298, "h298")));
        forge.statuses.put("Vidocq/vauban@h298", status(CiState.PENDING));
    }

    static ForgeRepo repo(long id, String name, boolean archived) {
        return new ForgeRepo(id, ORG, name, "main", archived, false);
    }

    static CombinedStatus status(CiState state) {
        return new CombinedStatus(state, List.of(new StatusContext("ci / build", state, "https://x/1")));
    }

    static RawCommit commit(String sha, String message) {
        return new RawCommit(sha, ORG, "vauban", T0.minusSeconds(600), "Yann", "y@example.org", "yblazart", message,
                0, 0);
    }

    static ForgePull pull(int number, String headSha) {
        return new ForgePull(number, "PR " + number, "yblazart", "feat/" + number, headSha,
                "https://codefloe.com/Vidocq/vauban/pulls/" + number, T0.minusSeconds(3600), T0.minusSeconds(60),
                null, false);
    }

    PullsLoop loop() {
        return new PullsLoop(forge, repos, pullStates, sonarSnapshots, live, new ForgeLinks("https://codefloe.com"),
                new TestClock(T0));
    }

    private RepoView view(String name) {
        return live.snapshot(ORG).repos().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void mainAndPullStatesReachLiveSnapshot() {
        loop().tick(ORG);

        RepoView vauban = view("vauban");
        assertEquals(CiState.SUCCESS, vauban.mainState());
        assertEquals("c0ffee", vauban.mainSha());
        assertEquals("feat: last one", vauban.lastCommitTitle());
        assertEquals("https://codefloe.com/Vidocq/vauban", vauban.htmlUrl());
        assertEquals(1, vauban.pulls().size());
        assertEquals(CiState.PENDING, vauban.pulls().getFirst().state());
        assertEquals(1, vauban.pulls().getFirst().contexts().size());
        assertEquals(T0, live.snapshot(ORG).updatedAt());
    }

    @Test
    void notFoundRepoDoesNotStopOthers() {
        forge.repos.get(ORG).add(repo(2, "ghost", false));   // no status: the fake answers not found

        loop().tick(ORG);

        assertEquals(List.of("vauban"), live.snapshot(ORG).repos().stream().map(RepoView::name).toList());
    }

    /** Forgejo answers 404 on /pulls when pull requests are disabled (or on a mirror); the main state still counts. */
    @Test
    void repoWithoutPullsKeepsItsMainState() {
        forge.failAlways("listOpenPulls", "Vidocq/vauban", new ForgeNotFoundException("pulls of Vidocq/vauban"));

        loop().tick(ORG);

        assertEquals(CiState.SUCCESS, view("vauban").mainState());
        assertEquals(List.of(), view("vauban").pulls());
    }

    @Test
    void oneUnavailableRepoKeepsItsLastViewAndTheOthersGoOn() {
        forge.repos.get(ORG).add(repo(2, "foy", false));
        forge.statuses.put("Vidocq/foy@main", status(CiState.SUCCESS));
        loop().tick(ORG);
        forge.failAlways("getCombinedStatus", "Vidocq/foy", new ForgeUnavailableException("500 on foy", null));
        forge.statuses.put("Vidocq/vauban@main", status(CiState.FAILURE));

        loop().tick(ORG);

        assertEquals(CiState.FAILURE, view("vauban").mainState());
        assertEquals(CiState.SUCCESS, view("foy").mainState());
    }

    @Test
    void everyRepoUnavailableFailsThePass() {
        loop().tick(ORG);
        forge.failAlways("getCombinedStatus", "Vidocq/vauban", new ForgeUnavailableException("CodeFloe down", null));
        var before = live.snapshot(ORG);

        assertThrows(ForgeUnavailableException.class, () -> loop().tick(ORG));
        assertSame(before, live.snapshot(ORG));
    }

    /** A restart while CodeFloe is down: the projects stored by the last pass, not an empty screen. */
    @Test
    void restartShowsTheStoredProjectsBeforeAnyPass() {
        loop().tick(ORG);
        live = new LiveState(new ChangeBus());   // restarted: nothing in memory
        forge.failAlways("getCombinedStatus", "Vidocq/vauban", new ForgeUnavailableException("CodeFloe down", null));

        loop().restore(ORG);

        assertEquals(List.of(298), view("vauban").pulls().stream().map(p -> p.number()).toList());
        assertEquals(CiState.PENDING, view("vauban").pulls().getFirst().state());
        assertEquals(CiState.NONE, view("vauban").mainState());   // not stored: unknown until the first pass
    }

    @Test
    void restoreNeverReplacesALivePass() {
        loop().tick(ORG);
        var before = live.snapshot(ORG);

        loop().restore(ORG);

        assertSame(before, live.snapshot(ORG));
    }

    @Test
    void archivedReposAreSkipped() {
        forge.repos.get(ORG).add(repo(3, "old", true));
        forge.statuses.put("Vidocq/old@main", status(CiState.FAILURE));

        loop().tick(ORG);

        assertEquals(List.of("vauban"), live.snapshot(ORG).repos().stream().map(RepoView::name).toList());
        assertTrue(repos.findById(3L).orElseThrow().getArchived());
    }

    @Test
    void refreshKeepsTheSonarOverrideAndTheCommitCursor() {
        var loop = loop();
        loop.refreshRepos(ORG);
        RepoRow row = repos.findById(1L).orElseThrow();
        row.setSonarKeyOverride("none");
        row.setLastCommitAt(T0);
        repos.save(row);

        loop.refreshRepos(ORG);

        assertEquals("none", repos.findById(1L).orElseThrow().getSonarKeyOverride());
        assertEquals(T0, repos.findById(1L).orElseThrow().getLastCommitAt());
    }

    @Test
    void openPullsArePersistedAndClosedOnesRemoved() {
        var loop = loop();
        loop.tick(ORG);
        assertEquals(1, pullStates.findByOrg(ORG).size());
        assertEquals(CiState.PENDING, pullStates.findById("Vidocq/vauban#298").orElseThrow().getCiState());

        forge.pulls.put("Vidocq/vauban", List.of());
        loop.tick(ORG);

        assertEquals(0, pullStates.findByOrg(ORG).size());
        assertEquals(0, view("vauban").pulls().size());
    }

    @Test
    void lastCommitIsReadWithoutStats() {
        loop().tick(ORG);
        assertEquals(1, forge.calls.get("latestCommit"));
        assertEquals(null, forge.calls.get("listCommits"));
    }

    static final String MAP = """
            {"components": [{"tag": "VAUBAN", "path": "core/vauban"}, {"tag": "FOY", "path": "web/foy"}]}""";

    /** A repository with a components.json at the root of its main branch: a monorepo. */
    private void monorepo() {
        forge.files.put("Vidocq/vauban@main:components.json", MAP);
        forge.pullFiles.put("Vidocq/vauban#298", List.of("web/foy/a.java", ".gitignore"));
    }

    private PullView thePull() {
        return view("vauban").pulls().getFirst();
    }

    /** In a monorepo, the components of a pull request's changed files reach its view. */
    @Test
    void theChangedFilesGiveTheirComponents() {
        monorepo();

        loop().tick(ORG);

        assertEquals(List.of("FOY"), thePull().fileTags());
        assertEquals(List.of("foy"), thePull().components());
    }

    /** The files of a pull request are read again only when its head changes. */
    @Test
    void theFilesAreReadAgainOnlyForANewHead() {
        monorepo();
        var loop = loop();
        loop.tick(ORG);
        loop.tick(ORG);
        assertEquals(1, forge.calls.get("listPullFiles"));

        forge.pulls.put("Vidocq/vauban", List.of(pull(298, "h298b")));
        forge.statuses.put("Vidocq/vauban@h298b", status(CiState.PENDING));
        forge.pullFiles.put("Vidocq/vauban#298", List.of("core/vauban/pom.xml"));
        loop.tick(ORG);

        assertEquals(2, forge.calls.get("listPullFiles"));
        assertEquals(List.of("VAUBAN"), thePull().fileTags());
    }

    /** components.json is read again only when the main branch gets a new commit. */
    @Test
    void theMapIsReadAgainOnlyForANewMainCommit() {
        monorepo();
        var loop = loop();
        loop.tick(ORG);
        loop.tick(ORG);
        assertEquals(1, forge.calls.get("readFile"));

        forge.commits.put("Vidocq/vauban", List.of(commit("beef", "feat: newer")));
        forge.files.put("Vidocq/vauban@main:components.json", """
                {"components": [{"tag": "FOYER", "path": "web"}]}""");
        loop.tick(ORG);

        assertEquals(2, forge.calls.get("readFile"));
        assertEquals(List.of("FOYER"), thePull().fileTags());
    }

    /** Files that cannot be read: the title only for this pass, read again on the next one. */
    @Test
    void unreadableFilesLeaveTheTitleAndAreReadAgain() {
        monorepo();
        var loop = loop();
        forge.failNext("listPullFiles", new ForgeUnavailableException("500 on files", null));
        loop.tick(ORG);
        assertEquals(List.of(), thePull().fileTags());
        assertEquals(1, view("vauban").pulls().size());

        loop.tick(ORG);

        assertEquals(List.of("FOY"), thePull().fileTags());
    }

    /** A components.json that cannot be read now: no file components this pass, read again on the next one. */
    @Test
    void anUnreadableMapIsReadAgain() {
        monorepo();
        var loop = loop();
        forge.failNext("readFile", new ForgeUnavailableException("500 on raw", null));
        loop.tick(ORG);
        assertEquals(List.of(), thePull().fileTags());

        loop.tick(ORG);

        assertEquals(List.of("FOY"), thePull().fileTags());
    }

    /** Without components.json, the files of the pull requests are never read. */
    @Test
    void withoutMapTheFilesAreNotRead() {
        loop().tick(ORG);

        assertEquals(null, forge.calls.get("listPullFiles"));
        assertEquals(List.of(), thePull().fileTags());
        assertEquals(List.of("vauban"), thePull().components());
    }
}
