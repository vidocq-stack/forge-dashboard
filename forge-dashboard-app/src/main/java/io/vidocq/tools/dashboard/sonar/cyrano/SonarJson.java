package io.vidocq.tools.dashboard.sonar.cyrano;

import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.sonar.SonarMeasures;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.annotation.JsonbProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The JSON of the Sonar Web API, as raw records, and its mapping to the port's types. */
public final class SonarJson {

    private static final Jsonb JSONB = JsonbBuilder.create();

    private SonarJson() {
    }

    public record RawMeasure(String metric, String value) {
    }

    public record RawComponent(String key, List<RawMeasure> measures) {
    }

    public record RawMeasures(RawComponent component) {
    }

    public record RawProjectStatus(String status) {
    }

    public record RawGate(@JsonbProperty("projectStatus") RawProjectStatus projectStatus) {
    }

    public static SonarMeasures toMeasures(String json) {
        RawMeasures raw = JSONB.fromJson(json, RawMeasures.class);
        Map<String, String> v = new HashMap<>();
        if (raw.component() != null && raw.component().measures() != null) {
            for (RawMeasure m : raw.component().measures()) {
                v.put(m.metric(), m.value());
            }
        }
        return new SonarMeasures(asLong(v.get("ncloc")), asLong(v.get("tests")), asDouble(v.get("coverage")),
                asLong(v.get("test_failures")), asLong(v.get("test_errors")), asLong(v.get("bugs")),
                asLong(v.get("vulnerabilities")), asLong(v.get("code_smells")),
                asDouble(v.get("duplicated_lines_density")), GateStatus.of(v.get("alert_status")));
    }

    public static GateStatus toGate(String json) {
        RawGate raw = JSONB.fromJson(json, RawGate.class);
        return raw.projectStatus() == null ? GateStatus.NONE : GateStatus.of(raw.projectStatus().status());
    }

    private static Long asLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.strip());
        } catch (NumberFormatException e) {
            Double d = asDouble(s);
            return d == null ? null : Math.round(d);
        }
    }

    private static Double asDouble(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
