package io.vidocq.tools.dashboard.domain;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The head of a Forgejo Actions job log: which runner took the task, and when. The Forgejo API does not link a job to
 * its runner; the first log line does.
 */
public record LogHead(String runnerUuid, long taskId, Instant at) {

    private static final Pattern RECEIVED =
            Pattern.compile("(?m)^(\\S+) Runner (\\S+) \\(version:[^)]*\\) received task (\\d+) ");

    /** The first line of {@code text} announcing a received task, if any. */
    public static Optional<LogHead> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        var m = RECEIVED.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LogHead(m.group(2), Long.parseLong(m.group(3)), Instant.parse(m.group(1))));
        } catch (DateTimeParseException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
