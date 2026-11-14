package io.vidocq.tools.dashboard.live;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/** The current snapshot of each organisation, replaced atomically by the loops. */
@ApplicationScoped
public class LiveState {

    private final Map<String, AtomicReference<OrgSnapshot>> snapshots = new ConcurrentHashMap<>();
    private final ChangeBus bus;

    @Inject
    public LiveState(ChangeBus bus) {
        this.bus = bus;
    }

    /** The current snapshot, {@link OrgSnapshot#empty} for an organisation not collected yet. */
    public OrgSnapshot snapshot(String org) {
        AtomicReference<OrgSnapshot> ref = snapshots.get(org);
        return ref == null ? OrgSnapshot.empty(org) : ref.get();
    }

    /** Replaces the snapshot of {@code org} with {@code change} applied to it, then publishes {@code event}. */
    public void update(String org, UnaryOperator<OrgSnapshot> change, ChangeEvent event) {
        snapshots.computeIfAbsent(org, o -> new AtomicReference<>(OrgSnapshot.empty(o))).updateAndGet(change);
        bus.publish(event);
    }
}
