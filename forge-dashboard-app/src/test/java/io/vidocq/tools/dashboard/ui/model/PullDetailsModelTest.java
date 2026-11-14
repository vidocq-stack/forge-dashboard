package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.domain.CiState;
import io.vidocq.tools.dashboard.forge.StatusContext;
import io.vidocq.tools.dashboard.live.PullView;
import io.vidocq.tools.dashboard.sonar.GateStatus;
import io.vidocq.tools.dashboard.store.JobExecution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PullDetailsModelTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final PullView PR = new PullView("vauban", 298, "feat: proxies", "yblazart", "sha", CiState.PENDING,
            List.of(new StatusContext("build / build (pull_request)", CiState.PENDING, "https://ci/1")),
            GateStatus.OK, "https://codefloe.com/Vidocq/vauban/pulls/298", NOW);

    private static JobExecution job(long taskId, long runId, String name, Long runnerId, String status,
            Instant started, Instant stopped) {
        JobExecution e = new JobExecution();
        e.setTaskId(taskId);
        e.setRunId(runId);
        e.setJobName(name);
        e.setRunnerId(runnerId);
        e.setStatus(status);
        e.setStartedAt(started);
        e.setStoppedAt(stopped);
        e.setHtmlUrl("https://codefloe.com/Vidocq/vauban/actions/runs/" + runId + "/jobs/" + taskId);
        return e;
    }

    @Test
    void pullDetailsListsJobsWithRunnerOrQuestionMark() {
        List<JobExecution> jobs = List.of(   // most recent first, as the repository gives them
                job(11, 7, "sonar", null, "running", NOW.minusSeconds(30), null),
                job(10, 7, "build", 1219L, "success", NOW.minusSeconds(200), NOW.minusSeconds(80)));

        PullDetails d = PullDetailsModel.of(PR, jobs, Map.of(1219L, "vidocq-runner-1"), NOW);

        assertEquals("feat: proxies", d.title());
        assertEquals("yblazart", d.author());
        assertEquals(CiState.PENDING, d.state());
        assertEquals(PR.contexts(), d.contexts());
        assertEquals(GateStatus.OK, d.sonarGate());
        assertEquals(PR.htmlUrl(), d.htmlUrl());
        assertEquals(List.of(
                new PullJobLine("build", "vidocq-runner-1", "success", "2 min 00 s",
                        "https://codefloe.com/Vidocq/vauban/actions/runs/7/jobs/10"),
                new PullJobLine("sonar", "?", "running", "30 s",
                        "https://codefloe.com/Vidocq/vauban/actions/runs/7/jobs/11")), d.lastRunJobs());
    }

    @Test
    void onlyTheLastRunIsListed() {
        List<JobExecution> jobs = List.of(
                job(21, 8, "build", 1219L, "running", NOW.minusSeconds(10), null),
                job(10, 7, "build", 1219L, "failure", NOW.minusSeconds(900), NOW.minusSeconds(800)));

        PullDetails d = PullDetailsModel.of(PR, jobs, Map.of(), NOW);

        assertEquals(1, d.lastRunJobs().size());
        assertEquals("?", d.lastRunJobs().getFirst().runnerName());
        assertEquals("running", d.lastRunJobs().getFirst().status());
    }

    @Test
    void noJobYetGivesAnEmptyList() {
        assertEquals(List.of(), PullDetailsModel.of(PR, List.of(), Map.of(), NOW).lastRunJobs());
    }
}
