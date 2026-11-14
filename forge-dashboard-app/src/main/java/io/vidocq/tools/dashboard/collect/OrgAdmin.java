package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.auth.DashboardUser;
import io.vidocq.tools.dashboard.forge.ForgeApi;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.store.TrackedOrg;
import io.vidocq.tools.dashboard.store.TrackedOrgRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adds and removes the orgs the dashboard tracks; admins only. An org is added when the service token can read its
 * runners; a removed org keeps its data and comes back as it was when added again. Each change reaches every open
 * UI through {@link ChangeEvent.OrgsChanged}, after the registry has dropped its cached list.
 */
@ApplicationScoped
public class OrgAdmin {

    static final String ADMINS_ONLY = "Réservé aux administrateurs du dashboard.";

    private final ForgeApi forge;
    private final TrackedOrgRepository orgs;
    private final OrgRegistry registry;
    private final ChangeBus bus;
    private final Clock clock;

    @Inject
    public OrgAdmin(ForgeApi forge, TrackedOrgRepository orgs, OrgRegistry registry, ChangeBus bus, Clock clock) {
        this.forge = forge;
        this.orgs = orgs;
        this.registry = registry;
        this.bus = bus;
        this.clock = clock;
    }

    public synchronized OrgAdminResult add(String org, DashboardUser by) {
        if (by == null || !by.admin()) {
            return new OrgAdminResult.Rejected(ADMINS_ONLY);
        }
        String name = org == null ? "" : org.strip();
        if (name.isEmpty()) {
            return new OrgAdminResult.Rejected("Indiquez le nom de l'org CodeFloe.");
        }
        List<TrackedOrg> all;
        try (var stream = orgs.findAll()) {
            all = stream.toList();
        }
        Optional<TrackedOrg> known = all.stream().filter(o -> o.getName().equalsIgnoreCase(name)).findFirst();
        if (known.isPresent() && Boolean.TRUE.equals(known.get().getActive())) {
            return new OrgAdminResult.Rejected(known.get().getName() + " est déjà suivie.");
        }
        boolean readable;
        try {
            readable = forge.canReadRunners(name);
        } catch (RuntimeException unreachable) {
            return new OrgAdminResult.Rejected("CodeFloe ne répond pas, réessayez plus tard ("
                    + unreachable.getMessage() + ").");
        }
        if (!readable) {
            return new OrgAdminResult.Rejected("Le jeton de service n'a pas accès aux runners de " + name
                    + " : l'org n'existe pas, ou son compte n'en est pas propriétaire.");
        }
        TrackedOrg tracked = known.orElseGet(() -> new TrackedOrg(name,
                all.stream().map(TrackedOrg::getPosition).filter(Objects::nonNull).max(Integer::compare).orElse(-1) + 1,
                true));
        tracked.setActive(true);
        tracked.setAddedBy(by.login());
        tracked.setAddedAt(clock.instant());
        orgs.save(tracked);
        changed();
        return new OrgAdminResult.Added(tracked.getName());
    }

    /** Stops tracking {@code org}: off the tabs and the collector, its data kept. */
    public synchronized void deactivate(String org, DashboardUser by) {
        if (by == null || !by.admin()) {
            throw new SecurityException(ADMINS_ONLY);
        }
        orgs.findByName(org).filter(o -> Boolean.TRUE.equals(o.getActive())).ifPresent(o -> {
            o.setActive(false);
            orgs.save(o);
            changed();
        });
    }

    private void changed() {
        registry.invalidate();
        bus.publish(new ChangeEvent.OrgsChanged());
    }
}
