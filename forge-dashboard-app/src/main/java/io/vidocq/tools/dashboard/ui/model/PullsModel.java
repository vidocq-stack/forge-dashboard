package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.Formats;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.PullView;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The PR tab of an org: every open PR of its projects. The red PRs ready for review first, then the others ready, then
 * the drafts; in each group, the latest change first.
 */
public final class PullsModel {

    private static final Comparator<PullView> ORDER = Comparator.comparingInt(PullsModel::group)
            .thenComparing(PullView::updatedAt, Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
            .thenComparing(PullView::repo).thenComparingInt(PullView::number);

    private PullsModel() {
    }

    public static List<PullRow> rows(OrgSnapshot snapshot, Instant now, Messages m) {
        return snapshot.repos().stream().flatMap(r -> r.pulls().stream())
                .sorted(ORDER)
                .map(p -> new PullRow(p, p.updatedAt() == null ? ProjectsModel.DASH
                        : Formats.relative(p.updatedAt(), now, m)))
                .toList();
    }

    /** 0 red and ready for review, 1 ready otherwise, 2 a draft. */
    private static int group(PullView p) {
        if (p.draft()) {
            return 2;
        }
        return new PullRow(p, null).error() ? 0 : 1;
    }
}
