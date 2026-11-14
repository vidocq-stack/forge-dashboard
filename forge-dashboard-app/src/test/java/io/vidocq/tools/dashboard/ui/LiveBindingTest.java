package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.server.Command;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent.PullsChanged;
import io.vidocq.tools.dashboard.live.ChangeEvent.RunnersChanged;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The binding of a view to the change bus, with a fake UI access that runs the command at once. */
class LiveBindingTest {

    private final ChangeBus bus = new ChangeBus();
    private final AtomicInteger refreshes = new AtomicInteger();

    @Test
    void burstOfEventsIsCoalesced() throws InterruptedException {
        LiveBinding.subscribe(bus, e -> true, Command::execute, refreshes::incrementAndGet);

        for (int i = 0; i < 10; i++) {
            bus.publish(new RunnersChanged("Vidocq"));
            Thread.sleep(10);
        }

        assertTrue(awaitRefreshes(1, Duration.ofMillis(600)), "no refresh within 600 ms");
        Thread.sleep(700);
        assertEquals(1, refreshes.get());
    }

    @Test
    void eventsAfterTheWindowRefreshAgain() throws InterruptedException {
        LiveBinding.subscribe(bus, e -> true, Command::execute, refreshes::incrementAndGet);

        bus.publish(new RunnersChanged("Vidocq"));
        assertTrue(awaitRefreshes(1, Duration.ofMillis(1000)));
        bus.publish(new RunnersChanged("Vidocq"));
        assertTrue(awaitRefreshes(2, Duration.ofMillis(1000)));
    }

    @Test
    void uninterestingEventsAreIgnored() throws InterruptedException {
        LiveBinding.subscribe(bus, e -> e.equals(new RunnersChanged("Vidocq")), Command::execute,
                refreshes::incrementAndGet);

        bus.publish(new PullsChanged("Vidocq"));
        bus.publish(new RunnersChanged("VidocqTools"));

        Thread.sleep(700);
        assertEquals(0, refreshes.get());
    }

    @Test
    void removedBindingNoLongerRefreshes() throws InterruptedException {
        ChangeBus.Registration binding = LiveBinding.subscribe(bus, e -> true, Command::execute,
                refreshes::incrementAndGet);

        bus.publish(new RunnersChanged("Vidocq"));
        binding.remove();
        bus.publish(new RunnersChanged("Vidocq"));

        Thread.sleep(700);
        assertEquals(0, refreshes.get());
    }

    private boolean awaitRefreshes(int count, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (refreshes.get() < count) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }
}
