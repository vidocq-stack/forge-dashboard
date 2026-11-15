package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectsModelTest {

    static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    static RepoView repo(String name, CiState main, SonarMeasures sonar, List<PullView> pulls) {
        return new RepoView(name, main, "abc1234", "fix: something", NOW.minus(Duration.ofHours(2)), sonar, pulls,
                "https://codefloe.com/Vidocq/" + name);
    }

    static PullView pull(String repo, int number, CiState state) {
        return new PullView(repo, number, "PR " + number, "yblazart", "sha" + number, state, List.of(),
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

        assertEquals(new ProjectRow("vauban", CiState.SUCCESS, "41.8k", "2077", "86.9%", GateStatus.OK, 1, 0, 1,
                "il y a 2 h", "https://codefloe.com/Vidocq/vauban"), row);
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

    @Test
    void pullsWithoutStatusCountAsPendingAndFailuresAsRed() {
        ProjectRow row = rows(repo("foy", CiState.SUCCESS, null, List.of(pull("foy", 1, CiState.NONE),
                pull("foy", 2, CiState.FAILURE)))).getFirst();
        assertEquals(0, row.prGreen());
        assertEquals(1, row.prRed());
        assertEquals(1, row.prPending());
    }

    @Test
    void noCommitShowsDash() {
        RepoView empty = new RepoView("empty", CiState.NONE, null, null, null, null, List.of(), "u");
        assertEquals("—", rows(empty).getFirst().lastCommit());
    }
}
