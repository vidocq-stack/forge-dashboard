package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.store.TrackedOrg;
import io.vidocq.tools.dashboard.store.TrackedOrgRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tracked organisations, against the PostgreSQL of the dev services. */
class OrgRegistryIntegrationTest {

    private static TrackedOrgRepository orgs;

    @BeforeAll
    static void startApplication() {
        orgs = InProcessApp.bean(TrackedOrgRepository.class);
    }

    @BeforeEach
    void reset() {
        InProcessApp.truncate("tracked_org");
    }

    @Test
    void initialOrgsAreSeededOnceInOrder() {
        var registry = new OrgRegistry(orgs);

        registry.seedIfEmpty(List.of("Vidocq", " VidocqTools ", ""));
        registry.seedIfEmpty(List.of("AkoyoGames"));

        assertEquals(List.of("Vidocq", "VidocqTools"), registry.activeOrgs());
    }

    @Test
    void inactiveOrgsAreLeftOut() {
        orgs.save(new TrackedOrg("VidocqTools", 1, true));
        orgs.save(new TrackedOrg("Old", 0, false));
        orgs.save(new TrackedOrg("Vidocq", 0, true));

        assertEquals(List.of("Vidocq", "VidocqTools"), new OrgRegistry(orgs).activeOrgs());
    }

    @Test
    void activeOrgsAreCachedUntilInvalidated() {
        var registry = new OrgRegistry(orgs);
        registry.seedIfEmpty(List.of("Vidocq"));
        assertEquals(List.of("Vidocq"), registry.activeOrgs());

        orgs.save(new TrackedOrg("AkoyoGames", 1, true));
        assertEquals(List.of("Vidocq"), registry.activeOrgs());

        registry.invalidate();
        assertEquals(List.of("Vidocq", "AkoyoGames"), registry.activeOrgs());
    }
}
