package io.vidocq.tools.dashboard.ui;

import com.vaadin.flow.component.html.Span;
import io.vidocq.tools.dashboard.i18n.Messages;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.live.LoopHealth;
import io.vidocq.tools.dashboard.ui.model.Freshness;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * The age of the latest collector pass ("updated 8 s ago"), or "stale" in orange with the late loops in its tooltip,
 * in the user's language. It moves on its own: every 5 s, and
 * at each health change of the collector, by push.
 */
public class FreshnessBadge extends Span {

    private final Messages messages = Texts.current();

    static final Duration TICK = Duration.ofSeconds(5);

    private final Supplier<List<LoopHealth>> health;
    private final Clock clock;

    public FreshnessBadge(Supplier<List<LoopHealth>> health, Clock clock, ChangeBus bus) {
        this.health = health;
        this.clock = clock;
        getStyle().set("font-size", "var(--lumo-font-size-s)").set("white-space", "nowrap");
        refresh();
        LiveBinding.bind(this, bus, e -> e instanceof ChangeEvent.HealthChanged, this::refresh);
        LiveBinding.every(this, TICK, this::refresh);
    }

    void refresh() {
        Freshness f = Freshness.of(health.get(), clock.instant(), messages);
        setText(f.text());
        getElement().setAttribute("title", f.details());
        getStyle().set("color", f.stale()
                ? "var(--lumo-warning-text-color, #b25a00)"
                : "var(--lumo-secondary-text-color)");
        getStyle().set("font-weight", f.stale() ? "600" : "normal");
    }
}
