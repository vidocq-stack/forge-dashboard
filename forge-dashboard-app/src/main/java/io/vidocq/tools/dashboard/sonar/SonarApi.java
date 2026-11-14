package io.vidocq.tools.dashboard.sonar;

import java.util.Optional;

/**
 * Read access to SonarQube. Every method answers {@code empty} for an unknown project, and when Sonar is disabled;
 * other failures throw {@link SonarUnavailableException}.
 */
public interface SonarApi {

    /** Whether a Sonar token is configured: without it, no call is made. */
    boolean enabled();

    Optional<SonarMeasures> measures(String key);

    Optional<GateStatus> gate(String key);

    Optional<GateStatus> pullRequestGate(String key, int pr);
}
