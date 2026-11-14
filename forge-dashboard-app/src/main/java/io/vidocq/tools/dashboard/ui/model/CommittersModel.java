package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CommitKind;
import io.vidocq.tools.dashboard.store.CommitterAggregate;
import io.vidocq.tools.dashboard.store.PrCounts;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The committers view from the aggregates of {@code StatsQueries}: bots apart, the agent commits of a bot account
 * with the human of their PR, those without PR on the {@code ?agent} line.
 */
public final class CommittersModel {

    /** The human key of the agent commits whose PR author is unknown ({@code CommitsLoop.UNATTRIBUTED_AGENT}). */
    public static final String UNATTRIBUTED = "?agent";

    private CommittersModel() {
    }

    public static CommittersView of(List<CommitterAggregate> aggregates, List<PrCounts> prs) {
        Map<String, PrCounts> prsByLogin = prs.stream()
                .collect(Collectors.toMap(PrCounts::login, Function.identity(), (a, b) -> a));
        Map<String, Builder> humans = new LinkedHashMap<>();
        Map<String, Builder> automation = new LinkedHashMap<>();
        Builder unattributed = null;
        for (CommitterAggregate a : aggregates) {
            String key = a.humanKey() == null ? "?" : a.humanKey();
            Builder b;
            if (a.kind() == CommitKind.BOT) {
                b = automation.computeIfAbsent(key, Builder::new);
            } else if (UNATTRIBUTED.equals(key)) {
                b = unattributed == null ? unattributed = new Builder(key) : unattributed;
            } else {
                b = humans.computeIfAbsent(key, Builder::new);
            }
            b.add(a);
        }
        return new CommittersView(rows(humans, prsByLogin), rows(automation, prsByLogin),
                unattributed == null ? null : unattributed.build(prsByLogin));
    }

    private static List<CommitterRow> rows(Map<String, Builder> builders, Map<String, PrCounts> prs) {
        return builders.values().stream().map(b -> b.build(prs))
                .sorted(Comparator.comparingLong(CommitterRow::commits).reversed()
                        .thenComparing(CommitterRow::humanKey))
                .toList();
    }

    private static final class Builder {
        private final String key;
        private long commits;
        private long additions;
        private long deletions;
        private long alone;
        private final Map<String, Long> families = new HashMap<>();

        Builder(String key) {
            this.key = key;
        }

        void add(CommitterAggregate a) {
            commits += a.commits();
            additions += a.additions();
            deletions += a.deletions();
            if (a.agentFamilies() == null || a.agentFamilies().isBlank()) {
                alone += a.commits();
                return;
            }
            for (String family : a.agentFamilies().split(",")) {
                families.merge(family.strip(), a.commits(), Long::sum);
            }
        }

        CommitterRow build(Map<String, PrCounts> prs) {
            LinkedHashMap<String, Long> breakdown = new LinkedHashMap<>();
            if (alone > 0) {
                breakdown.put(CommitterRow.ALONE, alone);
            }
            families.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .forEach(e -> breakdown.put(e.getKey(), e.getValue()));
            PrCounts pr = prs.get(key);
            return new CommitterRow(key, commits, additions, deletions, pr == null ? 0 : pr.opened(),
                    pr == null ? 0 : pr.merged(), breakdown);
        }
    }
}
