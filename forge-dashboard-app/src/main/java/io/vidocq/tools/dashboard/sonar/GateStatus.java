package io.vidocq.tools.dashboard.sonar;

/** A Sonar quality gate status; {@code NONE} when there is no analysis. */
public enum GateStatus {
    OK, ERROR, WARN, NONE;

    public static GateStatus of(String s) {
        if (s == null) {
            return NONE;
        }
        return switch (s.strip()) {
            case "OK" -> OK;
            case "ERROR" -> ERROR;
            case "WARN" -> WARN;
            default -> NONE;
        };
    }
}
