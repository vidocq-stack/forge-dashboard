package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.Occupancy;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.RunnerView;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The CI board from the snapshot of the org and the occupancies computed by {@code OccupancyService}. */
public final class CiBoardModel {

    static final int HOURS = 24;

    private CiBoardModel() {
    }

    public static CiBoard of(OrgSnapshot snapshot, Map<Long, Occupancy> occupancy, Map<Long, List<Double>> hourly) {
        List<RunnerCard> cards = snapshot.runners().stream()
                .map(r -> card(r, occupancy.get(r.id()), hourly.get(r.id())))
                .sorted(Comparator.comparing(RunnerCard::offline)
                        .thenComparing(c -> c.name().toLowerCase(Locale.ROOT)))
                .toList();
        List<JobView> waiting = snapshot.waiting().stream()
                .sorted(Comparator.comparing(JobView::startedAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder())))
                .toList();
        return new CiBoard(cards, waiting, waiting.size());
    }

    private static RunnerCard card(RunnerView r, Occupancy occupancy, List<Double> hourly) {
        return new RunnerCard(r.id(), r.name(), r.status(), r.running().size() + "/" + r.capacity(), r.running(),
                occupancy == null ? 0.0 : occupancy.slotRatio(),
                hourly == null ? Collections.nCopies(HOURS, 0.0) : hourly);
    }
}
