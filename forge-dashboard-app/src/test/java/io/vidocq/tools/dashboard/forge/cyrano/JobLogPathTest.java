package io.vidocq.tools.dashboard.forge.cyrano;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobLogPathTest {

    private static final String HTML = "https://codefloe.com/Vidocq/vauban/actions/runs/1930/jobs/1/attempt/1";

    @Test
    void parsesTheJobPageOfTheConfiguredForge() {
        assertEquals(Optional.of(new JobLogPath("Vidocq", "vauban", 1930, 1, 1)),
                JobLogPath.parse(HTML, "https://codefloe.com"));
        assertEquals(Optional.of(new JobLogPath("Vidocq", "vauban", 1930, 1, 1)),
                JobLogPath.parse(HTML, "https://codefloe.com/"));
    }

    @Test
    void anotherHostIsRefused() {
        // The token goes only to the configured forge, whatever the API says.
        assertTrue(JobLogPath.parse("https://evil.example/Vidocq/vauban/actions/runs/1/jobs/1/attempt/1",
                "https://codefloe.com").isEmpty());
        assertTrue(JobLogPath.parse("https://codefloe.com.evil.example/Vidocq/vauban/actions/runs/1/jobs/1/attempt/1",
                "https://codefloe.com").isEmpty());
    }

    @Test
    void anotherPageIsRefused() {
        assertTrue(JobLogPath.parse("https://codefloe.com/Vidocq/vauban/pulls/12", "https://codefloe.com").isEmpty());
        assertTrue(JobLogPath.parse(null, "https://codefloe.com").isEmpty());
    }
}
