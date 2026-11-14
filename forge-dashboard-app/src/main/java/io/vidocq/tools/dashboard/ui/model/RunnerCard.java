package io.vidocq.tools.dashboard.ui.model;

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

    /** {@code ● actif}, {@code ○ inactif}, {@code ✕ hors ligne}, else the CodeFloe status as is. */
    public String label() {
        return label(status);
    }

    public static String label(String status) {
        return switch (status == null ? "" : status) {
            case "active" -> "● actif";
            case "idle" -> "○ inactif";
            case "offline" -> "✕ hors ligne";
            default -> status;
        };
    }

    public boolean offline() {
        return "offline".equals(status);
    }
}
