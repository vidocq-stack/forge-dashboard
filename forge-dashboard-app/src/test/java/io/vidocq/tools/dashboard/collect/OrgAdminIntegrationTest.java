package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.auth.DashboardUser;
import io.vidocq.tools.dashboard.forge.FakeForgeApi;
import io.vidocq.tools.dashboard.forge.ForgeUnavailableException;
import io.vidocq.tools.dashboard.live.ChangeBus;
import io.vidocq.tools.dashboard.live.ChangeEvent;
import io.vidocq.tools.dashboard.store.TrackedOrg;
import io.vidocq.tools.dashboard.store.TrackedOrgRepository;
import io.vidocq.tools.dashboard.testing.InProcessApp;
import io.vidocq.tools.dashboard.testing.TestClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adding and removing orgs, against a fake CodeFloe and the PostgreSQL of the dev services. */
class OrgAdminIntegrationTest {

    private static final DashboardUser ADMIN =
            new DashboardUser("yblazart", "Yann", null, Set.of("Vidocq:owners"), true);
    private static final DashboardUser DEV = new DashboardUser("dev", "Dev", null, Set.of("Vidocq:devs"), false);

    private FakeForgeApi forge;
    private OrgRegistry registry;
    private TrackedOrgRepository orgs;
    private final List<ChangeEvent> events = new CopyOnWriteArrayList<>();
    private OrgAdmin admin;

    @BeforeEach
    void twoTrackedOrgs() {
        InProcessApp.truncate("tracked_org");
        registry = InProcessApp.bean(OrgRegistry.class);
        registry.seedIfEmpty(List.of("Vidocq", "VidocqTools"));
        orgs = InProcessApp.bean(TrackedOrgRepository.class);
        forge = new FakeForgeApi();
        ChangeBus bus = new ChangeBus();
        bus.subscribe(events::add);
        admin = new OrgAdmin(forge, orgs, registry, bus, new TestClock(Instant.parse("2026-11-14T12:00:00Z")));
    }

    @Test
    void adminAddsAccessibleOrg() throws InterruptedException {
        assertEquals(new OrgAdminResult.Added("AkoyoGames"), admin.add("  AkoyoGames ", ADMIN));

        assertEquals(List.of("Vidocq", "VidocqTools", "AkoyoGames"), registry.activeOrgs());
        assertEquals("yblazart", orgs.findByName("AkoyoGames").orElseThrow().getAddedBy());
        awaitOrgsChanged();
    }

    @Test
    void inaccessibleOrgIsRejectedWithReason() {
        forge.runnersForbidden.add("AkoyoGames");
        assertEquals(new OrgAdminResult.Rejected("orgs.runnersUnreadable", List.of("AkoyoGames")),
                admin.add("AkoyoGames", ADMIN));
        assertTrue(orgs.findByName("AkoyoGames").isEmpty());
    }

    @Test
    void unreachableForgeIsRejectedWithReason() {
        forge.failNext("canReadRunners", new ForgeUnavailableException("CodeFloe answered 502", null));
        assertEquals(new OrgAdminResult.Rejected("orgs.forgeUnreachable", List.of("CodeFloe answered 502")),
                admin.add("AkoyoGames", ADMIN));
    }

    @Test
    void nonAdminIsRejected() {
        assertEquals(new OrgAdminResult.Rejected("orgs.adminsOnly", List.of()),
                admin.add("AkoyoGames", DEV));
        assertNull(forge.calls.get("canReadRunners"));
    }

    @Test
    void duplicateIsRejected() {
        assertEquals(new OrgAdminResult.Rejected("orgs.alreadyTracked", List.of("Vidocq")), admin.add("vidocq", ADMIN));
    }

    @Test
    void blankNameIsRejected() {
        assertEquals(new OrgAdminResult.Rejected("orgs.nameMissing", List.of()), admin.add("  ", ADMIN));
    }

    @Test
    void deactivatedOrgDisappearsFromTabs() throws InterruptedException {
        admin.deactivate("VidocqTools", ADMIN);

        assertEquals(List.of("Vidocq"), registry.activeOrgs());
        TrackedOrg kept = orgs.findByName("VidocqTools").orElseThrow();
        assertFalse(kept.getActive());
        awaitOrgsChanged();
    }

    @Test
    void deactivatedOrgCanBeAddedBack() {
        admin.deactivate("VidocqTools", ADMIN);

        assertEquals(new OrgAdminResult.Added("VidocqTools"), admin.add("VidocqTools", ADMIN));

        assertEquals(List.of("Vidocq", "VidocqTools"), registry.activeOrgs());
        try (var all = orgs.findAll()) {
            assertEquals(2, all.count());
        }
    }

    @Test
    void nonAdminCannotDeactivate() {
        assertThrows(SecurityException.class, () -> admin.deactivate("VidocqTools", DEV));
        assertEquals(List.of("Vidocq", "VidocqTools"), registry.activeOrgs());
    }

    private void awaitOrgsChanged() throws InterruptedException {
        for (int i = 0; i < 100 && !events.contains(new ChangeEvent.OrgsChanged()); i++) {
            Thread.sleep(10);
        }
        assertTrue(events.contains(new ChangeEvent.OrgsChanged()), events.toString());
    }
}
