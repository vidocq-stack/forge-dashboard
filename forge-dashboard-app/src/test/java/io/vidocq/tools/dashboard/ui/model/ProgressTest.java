package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.JobView;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProgressTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final Messages FR = Messages.forLocale(Locale.FRENCH);

    /** A job of vauban running for {@code elapsed}, which usually takes {@code usual} (null: no history). */
    static JobView running(long task, Integer pr, Duration elapsed, Duration usual) {
        return new JobView(task, task + 1000, "vauban", pr, null, "build", "running", NOW.minus(elapsed),
                "https://codefloe.com/Vidocq/vauban/actions/runs/1/jobs/" + task, 1219L, "r1", usual);
    }

    /** After the elapsed time a line already shows: how far along the job is. */
    @Test
    void aRunningJobSaysHowFarAlongAfterItsElapsedTime() {
        assertEquals(" · 60 % · reste ~2 min", Progress.after(NOW.minus(Duration.ofMinutes(3)),
                Duration.ofMinutes(5), NOW, FR));
        assertEquals(" · +1 min sur l'habitude", Progress.after(NOW.minus(Duration.ofMinutes(6)),
                Duration.ofMinutes(5), NOW, FR));
    }

    /** No history, or a job not started: the line stays as it was. */
    @Test
    void withoutAnEstimateNothingIsAdded() {
        assertEquals("", Progress.after(NOW.minus(Duration.ofMinutes(3)), null, NOW, FR));
        assertEquals("", Progress.after(null, Duration.ofMinutes(5), NOW, FR));
    }

    /** A build is as far along as its job with the most time left; jobs without history do not count. */
    @Test
    void aBuildStandsWhereItsLongestJobStands() {
        JobView twoLeft = running(1, null, Duration.ofMinutes(3), Duration.ofMinutes(5));
        JobView nineLeft = running(2, null, Duration.ofMinutes(1), Duration.ofMinutes(10));
        JobView unknown = running(3, null, Duration.ofMinutes(1), null);

        assertEquals("10 % · reste ~9 min", Progress.ofBuild(List.of(twoLeft, nineLeft, unknown), NOW, FR));
        assertNull(Progress.ofBuild(List.of(unknown), NOW, FR));
        assertNull(Progress.ofBuild(List.of(), NOW, FR));
    }
}
