package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.shared.Registration;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Keeps a component up to date while it is attached: the changes it is interested in refresh it through
 * {@code UI.access}, and so by push. Changes close together are merged: at most one refresh every 500 ms, the last
 * change of a burst always included.
 */
public final class LiveBinding {

    static final Duration WINDOW = Duration.ofMillis(500);

    private LiveBinding() {
    }

    /** Subscribes at each attach of {@code component}, unsubscribes at its detach; the registration ends both. */
    public static Registration bind(Component component, ChangeBus bus, Predicate<ChangeEvent> interest,
            Runnable refreshInUi) {
        return whileAttached(component, ui -> subscribe(bus, interest, command -> access(ui, command), refreshInUi));
    }

    /** Runs {@code refreshInUi} every {@code period} while {@code component} is attached, e.g. for "N s ago". */
    public static Registration every(Component component, Duration period, Runnable refreshInUi) {
        return whileAttached(component, ui -> {
            Thread ticker = Thread.ofVirtual().name("ui-ticker").start(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(period);
                        access(ui, refreshInUi::run);
                    }
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                }
            });
            return ticker::interrupt;
        });
    }

    /**
     * The bus subscription itself, with the UI access given: {@code ui::access} in the application, a direct call in
     * the tests. A burst of interesting changes runs {@code refresh} once, {@link #WINDOW} after its first change.
     */
    static ChangeBus.Registration subscribe(ChangeBus bus, Predicate<ChangeEvent> interest, Consumer<Command> access,
            Runnable refresh) {
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicBoolean pending = new AtomicBoolean();
        ChangeBus.Registration subscription = bus.subscribe(event -> {
            if (!active.get() || !interest.test(event) || !pending.compareAndSet(false, true)) {
                return;
            }
            Thread.ofVirtual().name("ui-refresh").start(() -> {
                try {
                    Thread.sleep(WINDOW);
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    return;
                }
                pending.set(false);
                if (active.get()) {
                    access.accept(refresh::run);
                }
            });
        });
        return () -> {
            active.set(false);
            subscription.remove();
        };
    }

    private static Registration whileAttached(Component component,
            java.util.function.Function<UI, ChangeBus.Registration> start) {
        AtomicReference<ChangeBus.Registration> running = new AtomicReference<>();
        Registration onAttach = component.addAttachListener(e -> replace(running, start.apply(e.getUI())));
        Registration onDetach = component.addDetachListener(e -> replace(running, null));
        component.getUI().ifPresent(ui -> replace(running, start.apply(ui)));
        return () -> {
            onAttach.remove();
            onDetach.remove();
            replace(running, null);
        };
    }

    private static void replace(AtomicReference<ChangeBus.Registration> running, ChangeBus.Registration next) {
        ChangeBus.Registration previous = running.getAndSet(next);
        if (previous != null) {
            previous.remove();
        }
    }

    private static void access(UI ui, Command command) {
        try {
            ui.access(command);
        } catch (UIDetachedException gone) {
            // closed between the change and the refresh: nothing to update
        }
    }
}
