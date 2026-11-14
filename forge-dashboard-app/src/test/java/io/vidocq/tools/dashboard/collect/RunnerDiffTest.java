package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.ForgeJob;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.vidocq.tools.dashboard.collect.ForgeFixtures.job;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RunnerDiffTest {

    @Test
    void diffSeparatesNewFinishedAndWaiting() {
        var r = RunnerDiff.diff(Set.of(1L, 2L), List.of(job(2, "running"), job(3, "running"), job(4, "waiting")));
        assertEquals(List.of(3L), r.newRunning().stream().map(ForgeJob::taskId).toList());
        assertEquals(Set.of(1L), r.finishedTaskIds());
        assertEquals(List.of(job(4, "waiting").id()), r.waiting().stream().map(ForgeJob::id).toList());
    }

    @Test
    void nothingActiveFinishesEverythingKnown() {
        var r = RunnerDiff.diff(Set.of(1L, 2L), List.of());
        assertEquals(Set.of(1L, 2L), r.finishedTaskIds());
        assertEquals(List.of(), r.newRunning());
    }
}
