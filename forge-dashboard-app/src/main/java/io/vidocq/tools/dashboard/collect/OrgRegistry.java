package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.store.TrackedOrg;
import io.vidocq.tools.dashboard.store.TrackedOrgRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The organisations the dashboard follows, in tab order. They are read from the database once and kept until
 * {@link #invalidate()}: whoever changes them calls it before publishing {@code OrgsChanged}, so that a UI reading
 * the organisations on that event never gets the old list.
 */
@ApplicationScoped
public class OrgRegistry {

    static final String SEEDED_BY = "DASHBOARD_INITIAL_ORGS";

    private final TrackedOrgRepository orgs;
    private volatile List<String> active;

    @Inject
    public OrgRegistry(TrackedOrgRepository orgs) {
        this.orgs = orgs;
    }

    public List<String> activeOrgs() {
        List<String> cached = active;
        if (cached == null) {
            cached = orgs.findByActiveTrueOrderByPositionAsc().stream().map(TrackedOrg::getName).toList();
            active = cached;
        }
        return cached;
    }

    public void invalidate() {
        active = null;
    }

    /** On the very first start only: the table is empty. Blank names and duplicates are skipped. */
    public synchronized void seedIfEmpty(List<String> initialOrgs) {
        boolean empty;
        try (var all = orgs.findAll()) {
            empty = all.findAny().isEmpty();
        }
        if (!empty) {
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        for (String name : initialOrgs) {
            if (name != null && !name.isBlank()) {
                names.add(name.strip());
            }
        }
        int position = 0;
        for (String name : names) {
            var org = new TrackedOrg(name, position++, true);
            org.setAddedBy(SEEDED_BY);
            org.setAddedAt(Instant.now());
            orgs.save(org);
        }
        invalidate();
    }
}
