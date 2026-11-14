package io.vidocq.tools.dashboard.live;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveStateTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private static RunnerView runner(long id) {
        return new RunnerView(id, "runner-" + id, "idle", List.of("docker"), 2, List.of());
    }

    @Test
    void unknownOrgIsEmpty() {
        var live = new LiveState(new ChangeBus());
        var s = live.snapshot("Vidocq");
        assertEquals(OrgSnapshot.empty("Vidocq"), s);
        assertTrue(s.repos().isEmpty());
        assertTrue(s.runners().isEmpty());
    }

    @Test
    void updatePublishesEventAndReplacesSnapshot() throws InterruptedException {
        var bus = new ChangeBus();
        var live = new LiveState(bus);
        BlockingQueue<ChangeEvent> received = new LinkedBlockingQueue<>();
        bus.subscribe(received::add);

        live.update("Vidocq", s -> s.withRunners(List.of(runner(7)), List.of(), NOW),
                new ChangeEvent.RunnersChanged("Vidocq"));

        assertEquals(List.of(runner(7)), live.snapshot("Vidocq").runners());
        assertEquals(NOW, live.snapshot("Vidocq").updatedAt());
        assertEquals(new ChangeEvent.RunnersChanged("Vidocq"), received.poll(1, TimeUnit.SECONDS));
        assertTrue(live.snapshot("VidocqTools").runners().isEmpty());
    }

    @Test
    void liveStateKeepsSnapshotWhenNoUpdate() {
        var live = new LiveState(new ChangeBus());
        live.update("Vidocq", s -> s.withRunners(List.of(runner(7)), List.of(), NOW),
                new ChangeEvent.RunnersChanged("Vidocq"));
        var first = live.snapshot("Vidocq");

        assertSame(first, live.snapshot("Vidocq"));
        assertEquals(1, live.snapshot("Vidocq").runners().size());
    }

    @Test
    void withReposKeepsRunnersAndWithRunnersKeepsRepos() {
        var repo = new RepoView("vauban", null, null, null, null, null, List.of(), "https://codefloe.com/Vidocq/vauban");
        var s = OrgSnapshot.empty("Vidocq")
                .withRunners(List.of(runner(7)), List.of(), NOW)
                .withRepos(List.of(repo), NOW.plusSeconds(1));
        assertEquals(List.of(runner(7)), s.runners());
        assertEquals(List.of(repo), s.repos());
        assertEquals(NOW.plusSeconds(1), s.updatedAt());
    }

    @Test
    void loopHealthIsStaleAfterThreeCadences() {
        var cadence = Duration.ofSeconds(15);
        assertTrue(new LoopHealth("runners", null, null, null, cadence).stale(NOW));
        assertFalse(new LoopHealth("runners", NOW.minusSeconds(45), null, null, cadence).stale(NOW));
        assertTrue(new LoopHealth("runners", NOW.minusSeconds(46), NOW, "boom", cadence).stale(NOW));
    }
}
