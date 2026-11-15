package io.vidocq.tools.dashboard.ui.model;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * How far along the running builds of an org's projects are (see {@link Progress#ofBuild}): main's, by project, while
 * it runs; each running PR's, by project and number.
 */
public record ProjectsProgress(Map<String, String> mains, Map<String, Map<Integer, String>> pulls) {

    public static final ProjectsProgress NONE = new ProjectsProgress(Map.of(), Map.of());

    public ProjectsProgress {
        mains = Map.copyOf(mains);
        pulls = pulls.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> Map.copyOf(e.getValue())));
    }

    /** Main's progress, null unless main runs with an estimate. */
    public String main(String repo) {
        return mains.get(repo);
    }

    /** The progress of PR {@code number}, null unless it runs with an estimate. */
    public String pull(String repo, int number) {
        return pulls.getOrDefault(repo, Map.of()).get(number);
    }
}
