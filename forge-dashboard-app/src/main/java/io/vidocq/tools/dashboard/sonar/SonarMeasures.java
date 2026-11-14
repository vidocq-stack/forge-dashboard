package io.vidocq.tools.dashboard.sonar;

/** The measures of a Sonar project; a metric Sonar does not report is {@code null}. */
public record SonarMeasures(Long ncloc, Long tests, Double coverage, Long testFailures, Long testErrors, Long bugs,
                            Long vulnerabilities, Long codeSmells, Double duplicatedLinesDensity, GateStatus gate) {
}
