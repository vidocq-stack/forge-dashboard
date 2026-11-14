package io.vidocq.tools.dashboard.forge;

import java.util.List;

/**
 * An Actions runner of an organisation.
 *
 * @param status {@code idle}, {@code active} or {@code offline}
 */
public record ForgeRunner(long id, String uuid, String name, String status, List<String> labels, String version) {

    public ForgeRunner {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}
