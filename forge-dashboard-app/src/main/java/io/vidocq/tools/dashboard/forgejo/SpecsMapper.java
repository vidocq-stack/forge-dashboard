package io.vidocq.tools.dashboard.forgejo;

import java.util.List;
import java.util.Locale;

/**
 * Mapping topics Forgejo -> libelles humains de specs Jakarta / MicroProfile.
 *
 * <p>Convention : un topic comme {@code jakarta-rest-4.0} est interprete comme une
 * declaration d'implementation de spec. Les topics non reconnus sont laisses tels quels.
 */
public final class SpecsMapper {

    private SpecsMapper() {}

    public static List<String> humanize(List<String> topics) {
        if (topics == null || topics.isEmpty()) return List.of();
        return topics.stream().map(SpecsMapper::format).toList();
    }

    private static String format(String topic) {
        var t = topic.toLowerCase(Locale.ROOT);
        if (t.startsWith("jakarta-")) {
            return "Jakarta " + capitalize(t.substring("jakarta-".length()).replace('-', ' '));
        }
        if (t.startsWith("microprofile-") || t.startsWith("mp-")) {
            var rest = t.startsWith("mp-")
                    ? t.substring(3)
                    : t.substring("microprofile-".length());
            return "MicroProfile " + capitalize(rest.replace('-', ' '));
        }
        return topic;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        var parts = s.split(" ");
        var out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(' ');
            var p = parts[i];
            if (p.isEmpty()) continue;
            // Versions et acronymes courts laisses tels quels
            if (p.matches("\\d.*") || p.length() <= 3) {
                out.append(p.toUpperCase(Locale.ROOT));
            } else {
                out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return out.toString();
    }
}
