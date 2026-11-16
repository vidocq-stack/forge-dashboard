package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.live.RunnerView;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectsModelTest {

    static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    static RepoView repo(String name, CiState main, SonarMeasures sonar, List<PullView> pulls) {
        return new RepoView(name, main, "abc1234", "fix: something", NOW.minus(Duration.ofHours(2)), sonar, pulls,
                "https://codefloe.com/Vidocq/" + name);
    }

    static PullView pull(String repo, int number, CiState state) {
        return pull(repo, number, state, "PR " + number);
    }

    static PullView pull(String repo, int number, CiState state, String title) {
        return new PullView(repo, number, title, "yblazart", "sha" + number, state, List.of(),
                GateStatus.NONE, "https://codefloe.com/Vidocq/" + repo + "/pulls/" + number, NOW);
    }

    static SonarMeasures sonar(Long ncloc, Long tests, Double coverage, GateStatus gate) {
        return new SonarMeasures(ncloc, tests, coverage, 0L, 0L, 0L, 0L, 12L, 1.5, gate);
    }

    private static List<ProjectRow> rows(RepoView... repos) {
        return ProjectsModel.rows(new OrgSnapshot("Vidocq", List.of(repos), List.of(), List.of(), NOW), NOW,
                Messages.forLocale(Locale.FRENCH));
    }

    @Test
    void rowCountsPullStatesAndFormatsSonar() {
        ProjectRow row = rows(repo("vauban", CiState.SUCCESS, sonar(41_814L, 2_077L, 86.9, GateStatus.OK),
                List.of(pull("vauban", 1, CiState.SUCCESS), pull("vauban", 2, CiState.PENDING)))).getFirst();

        assertEquals(new ProjectRow("vauban", CiState.SUCCESS, "41.8k", "2077", "86.9%", GateStatus.OK, 1, 0, 1, 0,
                "il y a 2 h", "https://codefloe.com/Vidocq/vauban", false), row);
    }

    @Test
    void missingSonarShowsDash() {
        ProjectRow row = rows(repo("cassini", CiState.SUCCESS, null, List.of())).getFirst();
        assertEquals("—", row.loc());
        assertEquals("—", row.tests());
        assertEquals("—", row.coverage());
        assertEquals(GateStatus.NONE, row.gate());
    }

    @Test
    void sonarWithoutCoverageShowsDashForIt() {
        ProjectRow row = rows(repo("ravel", CiState.SUCCESS, sonar(800L, null, null, null), List.of())).getFirst();
        assertEquals("800", row.loc());
        assertEquals("—", row.tests());
        assertEquals("—", row.coverage());
        assertEquals(GateStatus.NONE, row.gate());
    }

    @Test
    void redMainSortsFirst() {
        List<ProjectRow> rows = rows(repo("alpha", CiState.SUCCESS, null, List.of()),
                repo("zeta", CiState.FAILURE, null, List.of()), repo("Beta", CiState.PENDING, null, List.of()));
        assertEquals(List.of("zeta", "alpha", "Beta"), rows.stream().map(ProjectRow::repo).toList());
    }

    /** Errors first: a red main, then a red pull request, then a failed Sonar gate, whatever the coverage. */
    @Test
    void errorsComeFirstMainThenPullThenGate() {
        List<ProjectRow> rows = rows(
                repo("covered", CiState.SUCCESS, sonar(1_000L, 100L, 99.0, GateStatus.OK), List.of()),
                repo("gate", CiState.SUCCESS, sonar(1_000L, 100L, 95.0, GateStatus.ERROR), List.of()),
                repo("pull", CiState.SUCCESS, sonar(1_000L, 100L, 90.0, GateStatus.OK),
                        List.of(pull("pull", 1, CiState.SUCCESS), pull("pull", 2, CiState.FAILURE))),
                repo("main", CiState.FAILURE, sonar(1_000L, 100L, 10.0, GateStatus.OK), List.of()));
        assertEquals(List.of("main", "pull", "gate", "covered"), rows.stream().map(ProjectRow::repo).toList());
    }

    /**
     * A work in progress (a title starting with WIP or [WIP]) may fail: it neither lifts its project nor counts red.
     */
    @Test
    void failingWorkInProgressIsNotAnError() {
        List<ProjectRow> rows = rows(
                repo("erasmus", CiState.SUCCESS, sonar(1_000L, 100L, 50.0, GateStatus.OK),
                        List.of(pull("erasmus", 1, CiState.FAILURE, "WIP: the TCK drives the plan"),
                                pull("erasmus", 2, CiState.FAILURE, "[wip] pages"))),
                repo("vauban", CiState.SUCCESS, sonar(1_000L, 100L, 90.0, GateStatus.OK), List.of()));
        assertEquals(List.of("vauban", "erasmus"), rows.stream().map(ProjectRow::repo).toList());
        assertEquals(0, rows.getLast().prRed(), "the PR column does not show them red");
        assertEquals(2, rows.getLast().prWip(), "but as WIP");
    }

    /** Past the errors: the best covered first, then the most lines of code, then the most tests; no Sonar last. */
    @Test
    void thenHighestCoverageThenMostLocThenMostTests() {
        List<ProjectRow> rows = rows(
                repo("none", CiState.SUCCESS, null, List.of()),
                repo("small", CiState.SUCCESS, sonar(1_000L, 900L, 90.0, GateStatus.OK), List.of()),
                repo("low", CiState.SUCCESS, sonar(90_000L, 900L, 40.0, GateStatus.OK), List.of()),
                repo("fewTests", CiState.SUCCESS, sonar(5_000L, 50L, 90.0, GateStatus.OK), List.of()),
                repo("noCoverage", CiState.SUCCESS, sonar(9_000L, null, null, GateStatus.NONE), List.of()),
                repo("moreTests", CiState.SUCCESS, sonar(5_000L, 80L, 90.0, GateStatus.OK), List.of()));
        assertEquals(List.of("moreTests", "fewTests", "small", "low", "noCoverage", "none"),
                rows.stream().map(ProjectRow::repo).toList());
    }

    /**
     * Past the errors, the projects with a PR ready for review open (running or green) come first: the work under
     * way. A draft alone does not lift its project (WIP is neutral); in each group, the best covered first.
     */
    @Test
    void thenTheProjectsWithAReadyPullOpen() {
        List<ProjectRow> rows = rows(
                repo("quiet", CiState.SUCCESS, sonar(1_000L, 100L, 95.0, GateStatus.OK), List.of()),
                repo("drafting", CiState.SUCCESS, sonar(1_000L, 100L, 94.0, GateStatus.OK),
                        List.of(pull("drafting", 1, CiState.PENDING, "WIP: later"))),
                repo("vidocq", CiState.SUCCESS, sonar(60_000L, 4_000L, 86.9, GateStatus.OK),
                        List.of(pull("vidocq", 621, CiState.PENDING))),
                repo("reviewed", CiState.SUCCESS, sonar(1_000L, 100L, 90.0, GateStatus.OK),
                        List.of(pull("reviewed", 3, CiState.SUCCESS))),
                repo("red", CiState.FAILURE, sonar(1_000L, 100L, 50.0, GateStatus.OK), List.of()));

        assertEquals(List.of("red", "reviewed", "vidocq", "quiet", "drafting"),
                rows.stream().map(ProjectRow::repo).toList());
        assertEquals(List.of(true, false, false, false, false), rows.stream().map(ProjectRow::error).toList(),
                "a PR under way is not an error");
    }

    /** The rows the grid marks in red are the ones the sort puts first, for the same reasons. */
    @Test
    void errorRowsAreTheOnesSortedFirst() {
        List<ProjectRow> rows = rows(
                repo("main", CiState.FAILURE, null, List.of()),
                repo("pull", CiState.SUCCESS, null, List.of(pull("pull", 1, CiState.FAILURE))),
                repo("gate", CiState.SUCCESS, sonar(1_000L, 100L, 90.0, GateStatus.ERROR), List.of()),
                repo("draft", CiState.SUCCESS, null, List.of(pull("draft", 2, CiState.FAILURE, "WIP: x"))),
                repo("healthy", CiState.SUCCESS, sonar(1_000L, 100L, 90.0, GateStatus.OK), List.of()));
        assertEquals(List.of("main:true", "pull:true", "gate:true", "healthy:false", "draft:false"),
                rows.stream().map(r -> r.repo() + ":" + r.error()).toList());
    }

    @Test
    void pullsWithoutStatusCountAsPendingAndFailuresAsRed() {
        ProjectRow row = rows(repo("foy", CiState.SUCCESS, null, List.of(pull("foy", 1, CiState.NONE),
                pull("foy", 2, CiState.FAILURE)))).getFirst();
        assertEquals(0, row.prGreen());
        assertEquals(1, row.prRed());
        assertEquals(1, row.prPending());
    }

    /** A draft is not ready: whatever its state, it counts as WIP, never in green, red or running. */
    @Test
    void draftsCountApartAsWip() {
        ProjectRow row = rows(repo("foy", CiState.SUCCESS, null, List.of(pull("foy", 1, CiState.SUCCESS),
                pull("foy", 2, CiState.FAILURE, "WIP: websocket"), pull("foy", 3, CiState.PENDING, "[WIP] origin"),
                pull("foy", 4, CiState.SUCCESS, "wip: lower case too"), pull("foy", 5, CiState.FAILURE)))).getFirst();

        assertEquals(1, row.prGreen());
        assertEquals(1, row.prRed());
        assertEquals(0, row.prPending());
        assertEquals(3, row.prWip());
        assertEquals(5, row.pulls());
    }

    /** A job of {@code repo} running for {@code elapsedMinutes} that usually takes {@code usualMinutes}. */
    private static JobView job(long task, String repo, Integer pr, long elapsedMinutes, long usualMinutes) {
        return new JobView(task, task + 1000, repo, pr, null, "build", "running",
                NOW.minus(Duration.ofMinutes(elapsedMinutes)), "u", 1219L, "r1", Duration.ofMinutes(usualMinutes));
    }

    private static ProjectsProgress progress(RepoView repo, JobView... running) {
        RunnerView runner = new RunnerView(1219, "r1", "active", List.of(), 4, List.of(running));
        return ProjectsModel.progress(new OrgSnapshot("Vidocq", List.of(repo), List.of(runner), List.of(), NOW), NOW,
                Messages.forLocale(Locale.FRENCH));
    }

    /** A running main and the running PRs say how far along they are: as far as their longest job. */
    @Test
    void runningBuildsSayHowFarAlongTheyAre() {
        RepoView vauban = repo("vauban", CiState.PENDING, null,
                List.of(pull("vauban", 7, CiState.PENDING), pull("vauban", 8, CiState.SUCCESS)));

        ProjectsProgress p = progress(vauban, job(1, "vauban", null, 3, 5), job(2, "vauban", 7, 1, 10),
                job(3, "cyrano", null, 1, 2));

        assertEquals("60 % · reste ~2 min", p.main("vauban"));
        assertEquals("10 % · reste ~9 min", p.pull("vauban", 7));
        assertNull(p.pull("vauban", 8));
        assertNull(p.main("cyrano"), "cyrano is not a project of the snapshot");
    }

    /** A job without a PR while main is not running is on another branch: main says nothing. */
    @Test
    void mainSaysNothingUnlessItIsRunning() {
        ProjectsProgress p = progress(repo("vauban", CiState.SUCCESS, null, List.of()), job(1, "vauban", null, 3, 5));

        assertNull(p.main("vauban"));
    }

    @Test
    void noCommitShowsDash() {
        RepoView empty = new RepoView("empty", CiState.NONE, null, null, null, null, List.of(), "u");
        assertEquals("—", rows(empty).getFirst().lastCommit());
    }
}
