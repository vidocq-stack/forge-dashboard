package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.domain.Interval;
import io.vidocq.tools.dashboard.domain.Occupancy;
import io.vidocq.tools.dashboard.domain.OccupancyCalculator;
import io.vidocq.tools.dashboard.domain.StatusSpan;
import io.vidocq.tools.dashboard.store.RunnerRow;
import io.vidocq.tools.dashboard.store.RunnerRowRepository;
import io.vidocq.tools.dashboard.store.StatsQueries;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The occupancy of the runners of an org, from the stored jobs and status changes: over a window, and hour by hour
 * over the last 24 hours. A runner's capacity is its override, else the most jobs it held at once over 7 days. Each
 * result is computed at most once a minute per org and window: the CI views of every user share it.
 */
@ApplicationScoped
public class OccupancyService {

    static final Duration CACHE = Duration.ofMinutes(1);
    static final Duration CAPACITY_WINDOW = Duration.ofDays(7);
    static final int HOURS = 24;

    private record Cached<T>(T value, Instant at) {
    }

    private final StatsQueries stats;
    private final RunnerRowRepository runners;
    private final Clock clock;
    private final Map<String, Cached<?>> cache = new ConcurrentHashMap<>();

    @Inject
    public OccupancyService(StatsQueries stats, RunnerRowRepository runners, Clock clock) {
        this.stats = stats;
        this.runners = runners;
        this.clock = clock;
    }

    /** Per runner id, over {@code [now − window, now)}. */
    public Map<Long, Occupancy> occupancy(String org, Duration window) {
        return cached(org + "|" + window, () -> {
            Instant now = clock.instant();
            Instant from = now.minus(window);
            Map<Long, List<Interval>> jobs = byRunner(stats.jobIntervals(org, from, now), Interval::runnerId);
            Map<Long, List<StatusSpan>> statuses = byRunner(stats.statusChanges(org, from, now), StatusSpan::runnerId);
            Map<Long, Integer> capacity = capacities(org, now);
            Map<Long, Occupancy> result = new HashMap<>();
            capacity.forEach((id, slots) -> result.put(id, OccupancyCalculator.compute(jobs.getOrDefault(id, List.of()),
                    statuses.getOrDefault(id, List.of()), slots, from, now)));
            return Map.copyOf(result);
        });
    }

    /** Per runner id, the slot ratio of each of the last 24 hours, the last hour last. */
    public Map<Long, List<Double>> hourly(String org) {
        return cached(org + "|hourly", () -> {
            Instant now = clock.instant();
            Instant from = now.minus(Duration.ofHours(HOURS));
            Map<Long, List<Interval>> jobs = byRunner(stats.jobIntervals(org, from, now), Interval::runnerId);
            Map<Long, List<StatusSpan>> statuses = byRunner(stats.statusChanges(org, from, now), StatusSpan::runnerId);
            Map<Long, List<Double>> result = new HashMap<>();
            capacities(org, now).forEach((id, slots) -> {
                List<Double> hours = new ArrayList<>(HOURS);
                for (int h = 0; h < HOURS; h++) {
                    Instant start = from.plus(Duration.ofHours(h));
                    hours.add(OccupancyCalculator.compute(jobs.getOrDefault(id, List.of()),
                            statuses.getOrDefault(id, List.of()), slots, start, start.plus(Duration.ofHours(1)))
                            .slotRatio());
                }
                result.put(id, List.copyOf(hours));
            });
            return Map.copyOf(result);
        });
    }

    /** The capacity of each runner of the org, the same rule as the runner cards ({@code RunnersLoop}). */
    private Map<Long, Integer> capacities(String org, Instant now) {
        Map<Long, List<Interval>> week = byRunner(stats.jobIntervals(org, now.minus(CAPACITY_WINDOW), now),
                Interval::runnerId);
        Map<Long, Integer> capacity = new HashMap<>();
        for (RunnerRow r : runners.findByOrg(org)) {
            capacity.put(r.getId(), OccupancyCalculator.capacity(r.getCapacityOverride(),
                    week.getOrDefault(r.getId(), List.of())));
        }
        return capacity;
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Supplier<T> compute) {
        Instant now = clock.instant();
        Cached<?> hit = cache.get(key);
        if (hit != null && hit.at().plus(CACHE).isAfter(now)) {
            return (T) hit.value();
        }
        T value = compute.get();
        cache.put(key, new Cached<>(value, now));
        return value;
    }

    private static <T> Map<Long, List<T>> byRunner(List<T> items, java.util.function.Function<T, Long> runner) {
        return items.stream().collect(Collectors.groupingBy(runner));
    }
}
