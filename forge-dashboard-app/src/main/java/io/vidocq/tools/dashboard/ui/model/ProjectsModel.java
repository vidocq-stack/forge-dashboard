package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.domain.Formats;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.live.RepoView;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** How far along the running builds of the projects are: apart from the rows, it moves with every runners pass. */
    public static ProjectsProgress progress(OrgSnapshot snapshot, Instant now, Messages m) {
        Map<String, List<JobView>> running = snapshot.runners().stream().flatMap(r -> r.running().stream())
                .collect(Collectors.groupingBy(JobView::repo));
        Map<String, String> mains = new HashMap<>();
        Map<String, Map<Integer, String>> pulls = new HashMap<>();
        for (RepoView r : snapshot.repos()) {
            List<JobView> jobs = running.getOrDefault(r.name(), List.of());
            String main = mainProgress(r, jobs, now, m);
            if (main != null) {
                mains.put(r.name(), main);
            }
            Map<Integer, String> prs = pullProgress(jobs, now, m);
            if (!prs.isEmpty()) {
                pulls.put(r.name(), prs);
            }
        }
        return new ProjectsProgress(mains, pulls);
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
        List<PullView> ready = r.pulls().stream().filter(p -> !p.draft()).toList();
        int green = count(ready, CiState.SUCCESS);
        int red = count(ready, CiState.FAILURE);
        return new ProjectRow(r.name(), r.mainState(),
                s == null || s.ncloc() == null ? DASH : Formats.compact(s.ncloc()),
                s == null || s.tests() == null ? DASH : Formats.compact(s.tests()),
                s == null || s.coverage() == null ? DASH : String.format(Locale.ROOT, "%.1f%%", s.coverage()),
                s == null || s.gate() == null ? GateStatus.NONE : s.gate(),
                green, red, ready.size() - green - red, r.pulls().size() - ready.size(),
                r.lastCommitAt() == null ? DASH : Formats.relative(r.lastCommitAt(), now, m),
                r.htmlUrl(), severity(r) < 3);
    }

    /**
     * Main's jobs are the running jobs of the project ({@code running}) without a PR, while main runs: otherwise they
     * belong to another branch.
     */
    private static String mainProgress(RepoView r, List<JobView> running, Instant now, Messages m) {
        return r.mainState() != CiState.PENDING ? null
                : Progress.ofBuild(running.stream().filter(j -> j.prNumber() == null).toList(), now, m);
    }

    private static Map<Integer, String> pullProgress(List<JobView> running, Instant now, Messages m) {
        Map<Integer, String> progress = new HashMap<>();
        running.stream().filter(j -> j.prNumber() != null).collect(Collectors.groupingBy(JobView::prNumber))
                .forEach((pr, jobs) -> {
                    String p = Progress.ofBuild(jobs, now, m);
                    if (p != null) {
                        progress.put(pr, p);
                    }
                });
        return progress;
    }

    private static int count(List<PullView> pulls, CiState state) {
        return (int) pulls.stream().filter(p -> p.state() == state).count();
    }
}
