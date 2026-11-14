package io.vidocq.tools.dashboard.sonar;

import java.util.Locale;
import java.util.Optional;

/** The Sonar project of a repository. */
public final class SonarKeys {

    /** The override that says "no Sonar project". */
    public static final String NONE = "none";

    private SonarKeys() {
    }

    /**
     * The override when set ({@code none} meaning no project); else {@code vidocq-<repo>} for the Vidocq organisation;
     * else no project.
     */
    public static Optional<String> keyFor(String org, String repo, Optional<String> override) {
        Optional<String> set = override.map(String::strip).filter(s -> !s.isEmpty());
        if (set.isPresent()) {
            return set.get().equalsIgnoreCase(NONE) ? Optional.empty() : set;
        }
        if (org != null && org.toLowerCase(Locale.ROOT).equals("vidocq")) {
            return Optional.of("vidocq-" + repo);
        }
        return Optional.empty();
    }
}
