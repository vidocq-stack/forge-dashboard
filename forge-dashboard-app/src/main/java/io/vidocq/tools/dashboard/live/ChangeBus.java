package io.vidocq.tools.dashboard.live;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Delivers the change events to the open UIs. Each listener is called on its own virtual thread: a slow or failing
 * listener never holds back a loop or another listener. Two events may therefore reach a listener out of order; a
 * listener reads the current state, never the event's payload.
 */
@ApplicationScoped
public class ChangeBus {

    private static final System.Logger LOG = System.getLogger(ChangeBus.class.getName());

    /** Ends a subscription. */
    @FunctionalInterface
    public interface Registration {
        void remove();
    }

    private final List<Consumer<ChangeEvent>> listeners = new CopyOnWriteArrayList<>();

    public Registration subscribe(Consumer<ChangeEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void publish(ChangeEvent event) {
        for (Consumer<ChangeEvent> listener : listeners) {
            Thread.ofVirtual().name("change-bus").start(() -> deliver(listener, event));
        }
    }

    private static void deliver(Consumer<ChangeEvent> listener, ChangeEvent event) {
        try {
            listener.accept(event);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "A change listener failed on " + event, e);
        }
    }
}
