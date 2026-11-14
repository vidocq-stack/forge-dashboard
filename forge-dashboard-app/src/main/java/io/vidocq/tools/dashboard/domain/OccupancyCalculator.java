package io.vidocq.tools.dashboard.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Runner occupancy from the stored job intervals and status changes. Pure. */
public final class OccupancyCalculator {

    private static final String OFFLINE = "offline";

    private OccupancyCalculator() {
    }

    /**
     * The occupancy of one runner over {@code [from, to)}: the jobs, clipped to the window (an open one runs to
     * {@code to}), against {@code capacity × (window − offline time)}. {@code statuses} are the changes of that runner,
     * starting with its last change before {@code from} (see {@code StatsQueries.statusChanges}).
     */
    public static Occupancy compute(List<Interval> jobs, List<StatusSpan> statuses, int capacity, Instant from,
                                    Instant to) {
        long window = nanos(from, to);
        long available = window - offline(statuses, from, to);
        if (window <= 0 || capacity <= 0 || available <= 0) {
            return new Occupancy(0, 0);
        }
        List<long[]> clipped = new ArrayList<>();
        for (Interval job : jobs) {
            long start = Math.max(0, nanos(from, job.start()));
            long stop = job.stop() == null ? window : Math.min(window, nanos(from, job.stop()));
            if (stop > start) {
                clipped.add(new long[] {start, stop});
            }
        }
        long slots = 0;
        for (long[] c : clipped) {
            slots += c[1] - c[0];
        }
        double slotRatio = Math.min(1.0, slots / ((double) capacity * available));
        double anyBusyRatio = Math.min(1.0, union(clipped) / (double) available);
        return new Occupancy(slotRatio, anyBusyRatio);
    }

    /** The largest number of jobs held at once, at least 1. Intervals are half-open: back to back is not together. */
    public static int observedCapacity(List<Interval> jobs) {
        record Edge(Instant at, int delta) {
        }
        List<Edge> edges = new ArrayList<>();
        for (Interval job : jobs) {
            edges.add(new Edge(job.start(), 1));
            if (job.stop() != null) {
                edges.add(new Edge(job.stop(), -1));
            }
        }
        // At the same instant, the job that stops leaves before the one that starts.
        edges.sort(Comparator.comparing(Edge::at).thenComparingInt(Edge::delta));
        int current = 0;
        int max = 1;
        for (Edge e : edges) {
            current += e.delta();
            max = Math.max(max, current);
        }
        return max;
    }

    /** The time of {@code [from, to)} spent offline: each change lasts until the next one, the last until {@code to}. */
    private static long offline(List<StatusSpan> statuses, Instant from, Instant to) {
        List<StatusSpan> sorted = statuses.stream().sorted(Comparator.comparing(StatusSpan::at)).toList();
        long total = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (!OFFLINE.equalsIgnoreCase(sorted.get(i).status())) {
                continue;
            }
            Instant start = max(sorted.get(i).at(), from);
            Instant stop = i + 1 < sorted.size() ? min(sorted.get(i + 1).at(), to) : to;
            if (stop.isAfter(start)) {
                total += nanos(start, stop);
            }
        }
        return total;
    }

    private static long union(List<long[]> intervals) {
        List<long[]> sorted = intervals.stream().sorted(Comparator.comparingLong(c -> c[0])).toList();
        long total = 0;
        long start = Long.MIN_VALUE;
        long stop = Long.MIN_VALUE;
        for (long[] c : sorted) {
            if (c[0] > stop) {
                total += stop - start;
                start = c[0];
                stop = c[1];
            } else {
                stop = Math.max(stop, c[1]);
            }
        }
        return total + (stop - start);
    }

    private static long nanos(Instant a, Instant b) {
        return Duration.between(a, b).toNanos();
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
