package io.vidocq.tools.dashboard.live;

import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChangeBusTest {

    @Test
    void failingListenerDoesNotBlockOthers() throws InterruptedException {
        var bus = new ChangeBus();
        BlockingQueue<ChangeEvent> received = new LinkedBlockingQueue<>();
        bus.subscribe(e -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(received::add);

        bus.publish(new ChangeEvent.PullsChanged("Vidocq"));

        assertEquals(new ChangeEvent.PullsChanged("Vidocq"), received.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void removedRegistrationStopsDelivery() throws InterruptedException {
        var bus = new ChangeBus();
        BlockingQueue<ChangeEvent> removed = new LinkedBlockingQueue<>();
        BlockingQueue<ChangeEvent> kept = new LinkedBlockingQueue<>();
        ChangeBus.Registration registration = bus.subscribe(removed::add);
        bus.subscribe(kept::add);

        registration.remove();
        bus.publish(new ChangeEvent.CommitsChanged());

        assertEquals(new ChangeEvent.CommitsChanged(), kept.poll(1, TimeUnit.SECONDS));
        assertNull(removed.poll(100, TimeUnit.MILLISECONDS));
    }
}
