package io.vidocq.tools.dashboard.forge.cyrano;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The coordinates of a job's web page, {@code <forge>/<owner>/<repo>/actions/runs/<run>/jobs/<job>/attempt/<n>}: the
 * log is served next to it, at {@code .../logs}. Only a page of the configured forge is accepted, so the token never
 * leaves it whatever the API returns.
 */
record JobLogPath(String owner, String repo, long run, long job, long attempt) {

    private static final Pattern PAGE =
            Pattern.compile("^/([^/]+)/([^/]+)/actions/runs/(\\d+)/jobs/(\\d+)/attempt/(\\d+)/?$");

    static Optional<JobLogPath> parse(String htmlUrl, String forgeUrl) {
        if (htmlUrl == null || forgeUrl == null) {
            return Optional.empty();
        }
        String base = forgeUrl.endsWith("/") ? forgeUrl.substring(0, forgeUrl.length() - 1) : forgeUrl;
        if (!htmlUrl.startsWith(base + "/")) {
            return Optional.empty();
        }
        var m = PAGE.matcher(htmlUrl.substring(base.length()));
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new JobLogPath(m.group(1), m.group(2), Long.parseLong(m.group(3)),
                Long.parseLong(m.group(4)), Long.parseLong(m.group(5))));
    }
}
