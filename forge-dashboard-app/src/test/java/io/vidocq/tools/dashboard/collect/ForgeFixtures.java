package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.forge.ForgeJob;
import io.vidocq.tools.dashboard.forge.ForgeRun;
import io.vidocq.tools.dashboard.forge.ForgeRunner;

import java.time.Instant;
import java.util.List;

/** Small builders of CodeFloe objects for the collector tests. */
final class ForgeFixtures {

    private ForgeFixtures() {
    }

    static ForgeJob job(long taskId, String status) {
        return job(taskId + 10_000, 86_414, taskId, status, "vidocq");
    }

    static ForgeJob job(long id, long runId, long taskId, String status, String repo) {
        return new ForgeJob(id, runId, 6285, taskId, "build", status, List.of("vidocq-runner"),
                "https://codefloe.com/Vidocq/" + repo + "/actions/runs/3906/jobs/0/attempt/1", null, null);
    }

    static ForgeJob finished(ForgeJob job, Instant started, Instant stopped, String status) {
        return new ForgeJob(job.id(), job.runId(), job.repoId(), job.taskId(), job.name(), status, job.runsOn(),
                job.htmlUrl(), started, stopped);
    }

    static ForgeRun run(String event, String prettyRef) {
        return new ForgeRun(86_414, event, prettyRef, "abc123", "feat: something", "running", null, null,
                "https://codefloe.com/Vidocq/vidocq/actions/runs/3906");
    }

    static ForgeRunner runner(long id, String uuid, String status) {
        return new ForgeRunner(id, uuid, "runner-" + id, status, List.of("vidocq-runner", "docker"), "v13.2.0");
    }

    /** The first line of a Forgejo job log, as the runner writes it. */
    static String logHead(Instant at, String runnerUuid, long taskId) {
        return at + " Runner " + runnerUuid + " (version:v13.2.0) received task " + taskId
                + " of job build, triggered by event: pull_request\n" + at + " workflow prepared\n";
    }
}
