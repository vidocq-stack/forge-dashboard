package io.vidocq.tools.dashboard.auth;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupGuardTest {

    @Test
    void groupGuard() {
        assertTrue(GroupGuard.allowed(Set.of("Vidocq:devs"), Set.of("Vidocq:owners", "Vidocq:devs")));
        assertFalse(GroupGuard.allowed(Set.of("VidocqTools"), Set.of("Vidocq:owners", "Vidocq:devs")));
    }

    @Test
    void noGroupIsNeverAllowed() {
        assertFalse(GroupGuard.allowed(Set.of(), Set.of("Vidocq:owners")));
        assertFalse(GroupGuard.allowed(Set.of("Vidocq:owners"), Set.of()));
    }
}
