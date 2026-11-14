package io.vidocq.tools.dashboard.live;

/** What changed in the live state; the listeners read the new state themselves. */
public sealed interface ChangeEvent permits ChangeEvent.RunnersChanged, ChangeEvent.PullsChanged,
        ChangeEvent.SonarChanged, ChangeEvent.CommitsChanged, ChangeEvent.HealthChanged, ChangeEvent.OrgsChanged {

    record RunnersChanged(String org) implements ChangeEvent {
    }

    record PullsChanged(String org) implements ChangeEvent {
    }

    record SonarChanged(String org) implements ChangeEvent {
    }

    record CommitsChanged() implements ChangeEvent {
    }

    record HealthChanged() implements ChangeEvent {
    }

    record OrgsChanged() implements ChangeEvent {
    }
}
