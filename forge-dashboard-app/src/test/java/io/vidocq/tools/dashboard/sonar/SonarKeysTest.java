package io.vidocq.tools.dashboard.sonar;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SonarKeysTest {

    @Test
    void vidocqOrgMapsToPrefixedKey() {
        assertEquals(Optional.of("vidocq-vauban"), SonarKeys.keyFor("Vidocq", "vauban", Optional.empty()));
        assertEquals(Optional.of("vidocq-vidocq"), SonarKeys.keyFor("vidocq", "vidocq", Optional.empty()));
        assertEquals(Optional.empty(), SonarKeys.keyFor("VidocqTools", "arago", Optional.empty()));
        assertEquals(Optional.of("io.vidocq.runtime:vidocq-runtime-build"),
                SonarKeys.keyFor("Vidocq", "vidocq", Optional.of("io.vidocq.runtime:vidocq-runtime-build")));
        assertEquals(Optional.empty(), SonarKeys.keyFor("Vidocq", "ci", Optional.of("none")));
    }

    @Test
    void blankOverrideIsIgnored() {
        assertEquals(Optional.of("vidocq-vauban"), SonarKeys.keyFor("Vidocq", "vauban", Optional.of("  ")));
    }

    @Test
    void overrideWinsForAnyOrg() {
        assertEquals(Optional.of("arago"), SonarKeys.keyFor("VidocqTools", "arago", Optional.of("arago")));
    }

    @Test
    void gateStatusOf() {
        assertEquals(GateStatus.OK, GateStatus.of("OK"));
        assertEquals(GateStatus.ERROR, GateStatus.of("ERROR"));
        assertEquals(GateStatus.WARN, GateStatus.of("WARN"));
        assertEquals(GateStatus.NONE, GateStatus.of("NONE"));
        assertEquals(GateStatus.NONE, GateStatus.of(null));
        assertEquals(GateStatus.NONE, GateStatus.of("whatever"));
    }
}
