package io.vidocq.tools.dashboard.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogHeadTest {

    @Test
    void parsesRunnerUuidTaskAndInstant() {
        var text = "2026-11-14T10:14:47.3061509Z Runner 87c30ab4-ebf0-4d81-bd76-1126048f2d0e (version:v13.0.0) "
                + "received task 175790 of job downstream, triggered by event: pull_request\n"
                + "2026-11-14T10:14:47.3129766Z workflow prepared\n";
        var h = LogHead.parse(text).orElseThrow();
        assertEquals("87c30ab4-ebf0-4d81-bd76-1126048f2d0e", h.runnerUuid());
        assertEquals(175790L, h.taskId());
        assertEquals(Instant.parse("2026-11-14T10:14:47.306150900Z"), h.at());
    }

    @Test
    void emptyOrForeignTextGivesEmpty() {
        assertTrue(LogHead.parse("").isEmpty());
        assertTrue(LogHead.parse(null).isEmpty());
        assertTrue(LogHead.parse("2026-11-14T10:14:47Z workflow prepared").isEmpty());
    }

    @Test
    void readsTheFirstMatchingLineOnly() {
        var text = "2026-11-14T10:00:00Z some preamble\n"
                + "2026-11-14T10:00:01Z Runner aaa (version:v13.0.0) received task 1 of job a\n"
                + "2026-11-14T10:00:02Z Runner bbb (version:v13.0.0) received task 2 of job b\n";
        var h = LogHead.parse(text).orElseThrow();
        assertEquals("aaa", h.runnerUuid());
        assertEquals(1L, h.taskId());
    }

    @Test
    void unparsableTimestampGivesEmpty() {
        assertTrue(LogHead.parse("yesterday Runner aaa (version:v1) received task 1 of job a").isEmpty());
    }
}
