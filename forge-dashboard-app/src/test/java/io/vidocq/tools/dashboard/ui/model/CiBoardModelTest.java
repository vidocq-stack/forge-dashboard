package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.Occupancy;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.live.OrgSnapshot;
import io.vidocq.tools.dashboard.live.RunnerView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CiBoardModelTest {

    private static final Messages FR = Messages.forLocale(Locale.FRENCH);

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    static JobView job(long task, String repo, Integer pr, Instant started) {
        return new JobView(task, task + 1000, repo, pr, pr == null ? "refs/heads/main" : "refs/pull/" + pr + "/head",
                "build", "running", started, "https://codefloe.com/Vidocq/" + repo + "/actions/runs/1/jobs/" + task,
                1219L, "r1", null);
    }

    static RunnerView runner(long id, String name, String status, int capacity, JobView... running) {
        return new RunnerView(id, name, status, List.of("vidocq-runner"), capacity, List.of(running));
    }

    private static CiBoard board(List<RunnerView> runners, List<JobView> waiting, Map<Long, Occupancy> occupancy,
            Map<Long, List<Double>> hourly) {
        return CiBoardModel.of(new OrgSnapshot("Vidocq", List.of(), runners, waiting, NOW), occupancy, hourly);
    }

    @Test
    void runnerCardShowsLoadAgainstCapacity() {
        List<Double> hours = Collections.nCopies(24, 0.25);
        RunnerCard card = board(List.of(runner(1219, "CodefloeRunnerMac", "active", 4,
                        job(1, "vauban", 298, NOW), job(2, "vauban", 298, NOW), job(3, "vidocq", null, NOW))),
                List.of(), Map.of(1219L, new Occupancy(0.42, 0.6)), Map.of(1219L, hours)).runners().getFirst();

        assertEquals("3/4", card.load());
        assertEquals(3, card.running().size());
        assertEquals(0.42, card.occupancy());
        assertEquals(hours, card.hourlyOccupancy());
        assertEquals("● actif", card.label(FR));
    }

    @Test
    void runnerWithoutOccupancyYetShowsZeroOverTwentyFourHours() {
        RunnerCard card = board(List.of(runner(7, "r7", "idle", 1)), List.of(), Map.of(), Map.of())
                .runners().getFirst();
        assertEquals(0.0, card.occupancy());
        assertEquals(Collections.nCopies(24, 0.0), card.hourlyOccupancy());
        assertEquals("○ inactif", card.label(FR));
    }

    @Test
    void waitingIsOldestFirst() {
        CiBoard b = board(List.of(), List.of(job(3, "a", 1, NOW.minusSeconds(10)), job(1, "b", 2, NOW.minusSeconds(90)),
                job(2, "c", 3, NOW.minusSeconds(40))), Map.of(), Map.of());
        assertEquals(List.of(1L, 2L, 3L), b.waiting().stream().map(JobView::taskId).toList());
        assertEquals(3, b.waitingCount());
    }

    @Test
    void offlineRunnersSortLast() {
        CiBoard b = board(List.of(runner(1, "alpha", "offline", 1), runner(2, "Charlie", "idle", 1),
                runner(3, "bravo", "active", 2)), List.of(), Map.of(), Map.of());
        assertEquals(List.of("bravo", "Charlie", "alpha"), b.runners().stream().map(RunnerCard::name).toList());
        assertEquals("✕ hors ligne", b.runners().getLast().label(FR));
    }

    @Test
    void runnerStatesInEnglish() {
        Messages en = Messages.forLocale(Locale.ENGLISH);
        assertEquals("○ idle", RunnerCard.label("idle", en));
        assertEquals("✕ offline", RunnerCard.label("offline", en));
        assertEquals("draining", RunnerCard.label("draining", en));
    }
}
