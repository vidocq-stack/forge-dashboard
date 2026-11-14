package io.vidocq.tools.dashboard.ui.model;

import io.vidocq.tools.dashboard.live.JobView;
import io.vidocq.tools.dashboard.store.JobExecution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunnerDetailsModelTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    private static JobExecution job(long task, String repo, Integer pr, String ref, int minutesAgo) {
        JobExecution e = new JobExecution();
        e.setTaskId(task);
        e.setJobId(task + 1000);
        e.setRunId(task / 10);
        e.setOrg("Vidocq");
        e.setRepo(repo);
        e.setPrNumber(pr);
        e.setRef(ref);
        e.setJobName("build");
        e.setStatus("success");
        e.setRunnerId(1219L);
        e.setStartedAt(NOW.minusSeconds(60L * minutesAgo));
        e.setStoppedAt(NOW.minusSeconds(60L * minutesAgo - 30));
        e.setHtmlUrl("https://codefloe.com/Vidocq/" + repo + "/actions/runs/1/jobs/" + task);
        return e;
    }

    @Test
    void recentJobsGroupByPr() {
        List<RunnerPrGroup> groups = RunnerDetailsModel.groups(List.of(   // newest first
                job(4, "vauban", 298, "refs/pull/298/head", 1),
                job(3, "vidocq", null, "refs/heads/main", 2),
                job(2, "vauban", 298, "refs/pull/298/head", 3),
                job(1, "vauban", 298, "refs/pull/298/head", 4)), 20);

        assertEquals(2, groups.size());
        RunnerPrGroup pr = groups.getFirst();
        assertEquals("vauban", pr.repo());
        assertEquals(298, pr.prNumber());
        assertEquals(List.of(4L, 2L, 1L), pr.jobs().stream().map(JobView::taskId).toList());
        assertEquals("vidocq", groups.get(1).repo());
        assertEquals("refs/heads/main", groups.get(1).ref());
        assertEquals(null, groups.get(1).prNumber());
    }

    @Test
    void pushJobsGroupByRef() {
        List<RunnerPrGroup> groups = RunnerDetailsModel.groups(List.of(
                job(2, "vidocq", null, "refs/heads/main", 1),
                job(1, "vidocq", null, "refs/heads/feat", 2)), 20);
        assertEquals(List.of("refs/heads/main", "refs/heads/feat"), groups.stream().map(RunnerPrGroup::ref).toList());
    }

    @Test
    void groupsAreCappedAtTheLimit() {
        List<JobExecution> jobs = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            jobs.add(job(100 + i, "vauban", 300 - i, "refs/pull/" + (300 - i) + "/head", i));
        }
        assertEquals(20, RunnerDetailsModel.groups(jobs, 20).size());
    }
}
