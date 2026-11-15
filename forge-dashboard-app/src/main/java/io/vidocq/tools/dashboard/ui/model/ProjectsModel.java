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

/** The projects grid of an org: red mains first, then by name; a PR without CI status counts as pending. */
public final class ProjectsModel {

    static final String DASH = "—";

    private ProjectsModel() {
    }

    public static List<ProjectRow> rows(OrgSnapshot snapshot, Instant now, Messages m) {
        return snapshot.repos().stream()
                .sorted(Comparator.comparing((RepoView r) -> r.mainState() != CiState.FAILURE)
                        .thenComparing(r -> r.name().toLowerCase(Locale.ROOT)))
                .map(r -> row(r, now, m))
                .toList();
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
                r.htmlUrl());
    }

    private static int count(List<PullView> pulls, CiState state) {
        return (int) pulls.stream().filter(p -> p.state() == state).count();
    }
}
