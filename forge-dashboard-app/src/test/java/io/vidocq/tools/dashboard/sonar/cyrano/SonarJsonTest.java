package io.vidocq.tools.dashboard.sonar.cyrano;

import io.vidocq.tools.dashboard.sonar.GateStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Real Sonar answers for vidocq-vauban, captured once. */
class SonarJsonTest {

    private static String read(String name) {
        try (var in = SonarJsonTest.class.getResourceAsStream("/fixtures/sonar/" + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void measuresFixtureMaps() {
        var m = SonarJson.toMeasures(read("measures.json"));
        assertEquals(42_030L, m.ncloc());
        assertEquals(2_090L, m.tests());
        assertEquals(86.9, m.coverage());
        assertEquals(0L, m.testFailures());
        assertEquals(0L, m.testErrors());
        assertEquals(0L, m.bugs());
        assertEquals(0L, m.vulnerabilities());
        assertEquals(0L, m.codeSmells());
        assertEquals(0.8, m.duplicatedLinesDensity());
        assertEquals(GateStatus.OK, m.gate());
    }

    @Test
    void missingMetricsAreNull() {
        var m = SonarJson.toMeasures("""
                {"component":{"key":"k","measures":[{"metric":"ncloc","value":"12"}]}}""");
        assertEquals(12L, m.ncloc());
        assertNull(m.tests());
        assertNull(m.coverage());
        assertEquals(GateStatus.NONE, m.gate());
    }

    @Test
    void gateFixtureMaps() {
        assertEquals(GateStatus.OK, SonarJson.toGate(read("gate.json")));
    }

    @Test
    void prGateFixtureMaps() {
        assertEquals(GateStatus.OK, SonarJson.toGate(read("gate-pr.json")));
    }
}
