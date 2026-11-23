package io.vidocq.tools.dashboard.domain;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The {@code components.json} of a monorepo: the tag of each component and its folder, in the file's order. A file
 * belongs to the component whose folder holds it, the longest folder when they are nested.
 */
public record ComponentMap(List<Component> components) {

    /** A repository without {@code components.json}, or with one that cannot be read. */
    public static final ComponentMap EMPTY = new ComponentMap(List.of());

    private static final Jsonb JSONB = JsonbBuilder.create();

    /** A component: its PR title tag (« VAUBAN ») and its folder, without a trailing « / ». */
    public record Component(String tag, String path) {
    }

    /** The raw file; its other fields (description, group, level) are not read. */
    public record RawMap(List<Component> components) {
    }

    public ComponentMap {
        components = List.copyOf(components);
    }

    /** The map of a {@code components.json}; {@link #EMPTY} when it cannot be read. */
    public static ComponentMap parse(String json) {
        try {
            RawMap raw = JSONB.fromJson(json, RawMap.class);
            if (raw == null || raw.components() == null) {
                return EMPTY;
            }
            return new ComponentMap(raw.components().stream()
                    .filter(c -> c != null && c.tag() != null && !c.tag().isBlank()
                            && c.path() != null && !c.path().isBlank())
                    .map(c -> new Component(c.tag(), strip(c.path())))
                    .toList());
        } catch (RuntimeException unreadable) {
            return EMPTY;
        }
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    /** The tags of the components that {@code files} touch, each once, in the order of the map. */
    public List<String> tagsOf(Collection<String> files) {
        Set<Integer> touched = new TreeSet<>();
        for (String file : files) {
            int best = -1;
            for (int i = 0; i < components.size(); i++) {
                String path = components.get(i).path();
                if (holds(path, file) && (best < 0 || path.length() > components.get(best).path().length())) {
                    best = i;
                }
            }
            if (best >= 0) {
                touched.add(best);
            }
        }
        return touched.stream().map(i -> components.get(i).tag()).toList();
    }

    private static boolean holds(String folder, String file) {
        return Objects.equals(folder, file) || file != null && file.startsWith(folder + "/");
    }

    private static String strip(String path) {
        String p = path.strip();
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
