package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.domain.Formats;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The projects grid of an org. Errors first: a red main, then a red pull request that is not a draft, then a failed
 * Sonar gate. Then the best covered, the most lines of code, the most tests (no measure last), and the name. A PR
 * without CI status counts as pending.
 */
public final class ProjectsModel {

    static final String DASH = "—";

    private static final Comparator<RepoView> ORDER = Comparator.comparingInt(ProjectsModel::severity)
            .thenComparing(r -> measure(r, SonarMeasures::coverage), Comparator.nullsLast(Comparator.<Double>reverseOrder()))
            .thenComparing(r -> measure(r, SonarMeasures::ncloc), Comparator.nullsLast(Comparator.<Long>reverseOrder()))
            .thenComparing(r -> measure(r, SonarMeasures::tests), Comparator.nullsLast(Comparator.<Long>reverseOrder()))
            .thenComparing(r -> r.name().toLowerCase(Locale.ROOT));

    private ProjectsModel() {
    }

    public static List<ProjectRow> rows(OrgSnapshot snapshot, Instant now, Messages m) {
        return snapshot.repos().stream()
                .sorted(ORDER)
                .map(r -> row(r, now, m))
                .toList();
    }

    /** 0 a red main, 1 a red pull request other than a draft, 2 a failed Sonar gate, 3 no error. */
    private static int severity(RepoView r) {
        if (r.mainState() == CiState.FAILURE) {
            return 0;
        }
        if (r.pulls().stream().anyMatch(p -> p.state() == CiState.FAILURE && !p.draft())) {
            return 1;
        }
        return r.sonar() != null && r.sonar().gate() == GateStatus.ERROR ? 2 : 3;
    }

    private static <T> T measure(RepoView r, Function<SonarMeasures, T> metric) {
        return r.sonar() == null ? null : metric.apply(r.sonar());
    }

    private static ProjectRow row(RepoView r, Instant now, Messages m) {
        SonarMeasures s = r.sonar();
        int green = count(r.pulls(), CiState.SUCCESS);
        int red = count(r.pulls(), CiState.FAILURE);
        return new ProjectRow(r.name(), r.mainState(),
                s == null || s.ncloc() == null ? DASH : Formats.compact(s.ncloc()),
                s == null || s.tests() == null ? DASH : Formats.compact(s.tests()),
                s == null || s.coverage() == null ? DASH : String.format(Locale.ROOT, "%.1f%%", s.coverage()),
                s == null || s.gate() == null ? GateStatus.NONE : s.gate(),
                green, red, r.pulls().size() - green - red,
                r.lastCommitAt() == null ? DASH : Formats.relative(r.lastCommitAt(), now, m),
                r.htmlUrl(), severity(r) < 3);
    }

    private static int count(List<PullView> pulls, CiState state) {
        return (int) pulls.stream().filter(p -> p.state() == state).count();
    }
}
