package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PullsModelTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final Messages FR = Messages.forLocale(Locale.FRENCH);

    private static PullView pull(String repo, int number, CiState state, String title, Duration updatedAgo) {
        return new PullView(repo, number, title, "yblazart", "sha" + number, state, List.of(), GateStatus.OK,
                "https://codefloe.com/Vidocq/" + repo + "/pulls/" + number, NOW.minus(updatedAgo));
    }

    private static RepoView repo(String name, PullView... pulls) {
        return new RepoView(name, CiState.SUCCESS, "abc", "fix: y", NOW.minus(Duration.ofDays(1)), null,
                List.of(pulls), "https://codefloe.com/Vidocq/" + name);
    }

    private static List<PullRow> rows(RepoView... repos) {
        return PullsModel.rows(new OrgSnapshot("Vidocq", List.of(repos), List.of(), List.of(), NOW), NOW, FR);
    }

    private static List<String> order(List<PullRow> rows) {
        return rows.stream().map(r -> r.pull().repo() + "#" + r.pull().number()).toList();
    }

    /** Every open PR of every project of the org, with when it last changed. */
    @Test
    void oneRowPerOpenPullOfTheOrg() {
        PullView seven = pull("vauban", 7, CiState.SUCCESS, "feat: proxies", Duration.ofHours(1));

        List<PullRow> rows = rows(repo("vauban", seven), repo("cyrano"));

        assertEquals(List.of(new PullRow(seven, "il y a 1 h")), rows);
    }

    /** The red PRs ready for review first; then the others ready, the latest change first; the drafts last. */
    @Test
    void readyRedFirstThenTheLatestThenTheDrafts() {
        List<PullRow> rows = rows(
                repo("vauban", pull("vauban", 7, CiState.SUCCESS, "feat: proxies", Duration.ofHours(1)),
                        pull("vauban", 8, CiState.FAILURE, "WIP: pages", Duration.ofMinutes(1))),
                repo("foy", pull("foy", 108, CiState.FAILURE, "fix: origin", Duration.ofHours(3)),
                        pull("foy", 110, CiState.PENDING, "feat: h2", Duration.ofMinutes(5)),
                        pull("foy", 111, CiState.NONE, "[WIP] h3", Duration.ofHours(2))));

        assertEquals(List.of("foy#108", "foy#110", "vauban#7", "vauban#8", "foy#111"), order(rows));
    }

    @Test
    void noOpenPullNoRow() {
        assertEquals(List.of(), rows(repo("cyrano")));
    }

    /** The components of the open PRs, sorted and without duplicates: the choices of the component filter. */
    @Test
    void componentsOfTheOpenPulls() {
        List<PullRow> rows = rows(
                repo("vauban", pull("vauban", 7, CiState.SUCCESS, "feat: proxies", Duration.ofHours(1))),
                repo("vidocq", pull("vidocq", 3, CiState.SUCCESS, "[VAUBAN][CASSINI] x", Duration.ofHours(2))),
                repo("foy", pull("foy", 1, CiState.SUCCESS, "fix: origin", Duration.ofHours(3))));

        assertEquals(List.of("cassini", "foy", "vauban"), PullsModel.components(rows));
    }

    /** The PRs of one component, in the order of {@code rows}; all of them when the component is null. */
    @Test
    void aComponentKeepsItsPulls() {
        List<PullRow> rows = rows(
                repo("vauban", pull("vauban", 7, CiState.SUCCESS, "feat: proxies", Duration.ofHours(1))),
                repo("vidocq", pull("vidocq", 3, CiState.SUCCESS, "[VAUBAN][CASSINI] x", Duration.ofHours(2))),
                repo("foy", pull("foy", 1, CiState.SUCCESS, "fix: origin", Duration.ofHours(3))));

        assertEquals(List.of("vauban#7", "vidocq#3"), order(PullsModel.of(rows, "vauban")));
        assertEquals(rows, PullsModel.of(rows, null));
    }
}
