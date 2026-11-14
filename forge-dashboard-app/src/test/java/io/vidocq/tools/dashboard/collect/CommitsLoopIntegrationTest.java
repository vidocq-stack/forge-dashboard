package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.config.CommitterConfig;
import io.vidocq.tools.dashboard.domain.CommitKind;
import io.vidocq.tools.dashboard.domain.RawCommit;
import io.vidocq.tools.dashboard.forge.FakeForgeApi;
import io.vidocq.tools.dashboard.forge.ForgePull;
import io.vidocq.tools.dashboard.forge.ForgeRepo;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.store.CommitStat;
import io.vidocq.tools.dashboard.store.CommitStatRepository;
import io.vidocq.tools.dashboard.store.PrStatRepository;
import io.vidocq.tools.dashboard.store.RepoRowRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.testing.TestClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The commits loop against a fake CodeFloe and the PostgreSQL of the dev services. */
class CommitsLoopIntegrationTest {

    private static final String ORG = "Vidocq";
    private static final Instant T0 = Instant.parse("2026-11-14T12:00:00Z");
    private static final String OPUS = "\n\nCo-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>\n"
            + "Claude-Session: https://claude.ai/code/session_01ABC";

    private static CommitStatRepository commits;
    private static PrStatRepository prs;
    private static RepoRowRepository repos;

    private FakeForgeApi forge;
    private ChangeBus bus;
    private TestClock clock;

    @BeforeAll
    static void startApplication() {
        commits = InProcessApp.bean(CommitStatRepository.class);
        prs = InProcessApp.bean(PrStatRepository.class);
        repos = InProcessApp.bean(RepoRowRepository.class);
    }

    @BeforeEach
    void reset() {
        InProcessApp.truncate("repo", "commit_stat", "pr_stat");
        forge = new FakeForgeApi();
        bus = new ChangeBus();
        clock = new TestClock(T0);
        forge.repos.put(ORG, List.of(new ForgeRepo(1, ORG, "vauban", "main", false, false)));
        new PullsLoop(forge, repos, InProcessApp.bean(io.vidocq.tools.dashboard.store.PullRequestStateRepository.class),
                InProcessApp.bean(io.vidocq.tools.dashboard.store.SonarSnapshotRepository.class),
                new io.vidocq.tools.dashboard.live.LiveState(bus),
                new io.vidocq.tools.dashboard.forge.ForgeLinks("https://codefloe.com"), clock).refreshRepos(ORG);
    }

    private CommitsLoop loop() {
        return new CommitsLoop(forge, commits, prs, repos, new CommitterConfig("", ""), bus, clock);
    }

    private static RawCommit commit(String sha, Instant at, String name, String login, String message) {
        return new RawCommit(sha, ORG, "vauban", at, name, name.toLowerCase().replace(' ', '.') + "@example.org",
                login, message, 10, 2);
    }

    /** Newest first, as CodeFloe lists them. */
    private void history(RawCommit... newestFirst) {
        forge.commits.put("Vidocq/vauban", List.of(newestFirst));
    }

    @Test
    void firstTickBackfillsAndStoresClassification() {
        history(commit("c3", T0.minusSeconds(100), "Vidocq Bot", null, "feat: by an agent" + OPUS),
                commit("c2", T0.minusSeconds(200), "vidocq-bot", null, "chore: release"),
                commit("c1", T0.minusSeconds(300), "Yann", "yblazart", "feat: with Opus" + OPUS));
        forge.pullAuthors.put("Vidocq/vauban@c3", "yblazart");

        loop().tick(ORG);

        assertEquals(3, commits.findAll().count());
        CommitStat c1 = commits.findById("Vidocq/vauban@c1").orElseThrow();
        assertEquals(CommitKind.HUMAN, c1.getKind());
        assertEquals("yblazart", c1.getHumanKey());
        assertEquals("Claude Opus", c1.getAgentFamilies());
        assertEquals("https://claude.ai/code/session_01ABC", c1.getClaudeSession());
        assertEquals(CommitKind.BOT, commits.findById("Vidocq/vauban@c2").orElseThrow().getKind());
        assertNull(commits.findById("Vidocq/vauban@c2").orElseThrow().getHumanKey());
        CommitStat c3 = commits.findById("Vidocq/vauban@c3").orElseThrow();
        assertEquals(CommitKind.AGENT_VIA_BOT, c3.getKind());
        assertEquals("yblazart", c3.getHumanKey());
        assertEquals(T0.minus(Duration.ofDays(90)), forge.commitSinces.getFirst());
    }

    @Test
    void secondTickIsIncremental() {
        history(commit("c1", T0.minusSeconds(300), "Yann", "yblazart", "feat: one"));
        var loop = loop();
        loop.tick(ORG);
        Instant cursor = repos.findById(1L).orElseThrow().getLastCommitAt();
        assertEquals(T0.minusSeconds(300), cursor);

        clock.advance(Duration.ofHours(1));
        history(commit("c2", T0.plusSeconds(600), "Yann", "yblazart", "feat: two"),
                commit("c1", T0.minusSeconds(300), "Yann", "yblazart", "feat: one"));
        forge.commitSinces.clear();
        loop.tick(ORG);

        assertEquals(cursor, forge.commitSinces.getFirst());
        assertEquals(2, commits.findAll().count());
        assertEquals(T0.plusSeconds(600), repos.findById(1L).orElseThrow().getLastCommitAt());
    }

    @Test
    void backfillIsCappedPerTick() {
        List<RawCommit> all = new ArrayList<>();
        for (int i = 0; i < 30 * 50; i++) {
            all.add(commit("s" + i, T0.minusSeconds(60L * (i + 1)), "Yann", "yblazart", "fix: " + i));
        }
        forge.commits.put("Vidocq/vauban", all);
        var loop = loop();

        loop.tick(ORG);
        assertEquals(20, forge.calls.get("listCommits"));
        assertEquals(20 * 50, commits.findAll().count());
        assertNull(repos.findById(1L).orElseThrow().getLastCommitAt());
        assertTrue(loop.backfillPending());

        loop.tick(ORG);
        assertEquals(30 * 50, commits.findAll().count());
        assertEquals(T0.minusSeconds(60), repos.findById(1L).orElseThrow().getLastCommitAt());
        assertFalse(loop.backfillPending());
    }

    @Test
    void quietRepositoryLeavesTheBackfillAfterOneTick() {
        history();
        var loop = loop();

        loop.tick(ORG);

        assertEquals(T0.minus(Duration.ofDays(90)), repos.findById(1L).orElseThrow().getLastCommitAt());
        assertFalse(loop.backfillPending());
    }

    @Test
    void agentViaBotWithoutPrIsUnattributed() {
        history(commit("c3", T0.minusSeconds(100), "Vidocq Bot", null, "feat: by an agent" + OPUS));

        loop().tick(ORG);

        assertEquals("?agent", commits.findById("Vidocq/vauban@c3").orElseThrow().getHumanKey());
    }

    @Test
    void pullRequestsAreCounted() {
        history(commit("c1", T0.minusSeconds(300), "Yann", "yblazart", "feat: one"));
        forge.pulls.put("Vidocq/vauban", List.of(new ForgePull(12, "PR", "yblazart", "feat/x", "h",
                "https://codefloe.com/Vidocq/vauban/pulls/12", T0.minus(Duration.ofDays(3)), T0.minusSeconds(60),
                T0.minusSeconds(60), false)));

        loop().tick(ORG);

        var p = prs.findById("Vidocq/vauban#12").orElseThrow();
        assertEquals("yblazart", p.getAuthorLogin());
        assertEquals(T0.minusSeconds(60), p.getMergedAt());
    }

    @Test
    void newCommitsArePublished() throws InterruptedException {
        BlockingQueue<ChangeEvent> received = new LinkedBlockingQueue<>();
        bus.subscribe(received::add);
        history(commit("c1", T0.minusSeconds(300), "Yann", "yblazart", "feat: one"));

        loop().tick(ORG);

        assertEquals(new ChangeEvent.CommitsChanged(), received.poll(1, TimeUnit.SECONDS));
    }
}
