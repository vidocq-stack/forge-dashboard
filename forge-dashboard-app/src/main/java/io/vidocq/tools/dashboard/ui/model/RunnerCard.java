package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.JobView;

import java.util.List;

/**
 * A runner of the CI board: its jobs in progress against its capacity ({@code "3/4"}), its occupancy over the chosen
 * window and, hour by hour, over the last 24 hours (the last hour last).
 */
public record RunnerCard(long runnerId, String name, String status, String load, List<JobView> running,
                         double occupancy, List<Double> hourlyOccupancy) {

    public RunnerCard {
        running = List.copyOf(running);
        hourlyOccupancy = List.copyOf(hourlyOccupancy);
    }

    /** {@code ● active}, {@code ○ idle}, {@code ✕ offline} in the given language, else the CodeFloe status as is. */
    public String label(Messages m) {
        return label(status, m);
    }

    public static String label(String status, Messages m) {
        return switch (status == null ? "" : status) {
            case "active", "idle", "offline" -> m.text("runner.state." + status);
            default -> status;
        };
    }

    public boolean offline() {
        return "offline".equals(status);
    }
}
