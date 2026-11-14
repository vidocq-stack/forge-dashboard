package io.vidocq.tools.dashboard.collect;

import io.vidocq.tools.dashboard.domain.LogHead;
import io.vidocq.tools.dashboard.forge.ForgeJob;
import io.vidocq.tools.dashboard.forge.ForgeRun;
import io.vidocq.tools.dashboard.store.JobExecution;
import io.vidocq.tools.dashboard.store.RunnerRow;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds the execution of a running job: its run, and its runner as the head of its log names it. Pure. */
public final class JobResolver {

    private static final Pattern PR_REF = Pattern.compile("^#(\\d+)$");

    private JobResolver() {
    }

    /**
     * The execution of {@code job}. The runner and the start come from {@code head} when it is the head of this task's
     * log and names a known runner; otherwise the runner stays null and the start is the job's own, else {@code now}.
     * {@code run} may be null when CodeFloe no longer has it.
     */
    public static JobExecution resolve(String org, ForgeJob job, ForgeRun run, String repo, Optional<LogHead> head,
                                       Map<String, RunnerRow> runnersByUuid, Instant now) {
        var e = new JobExecution();
        e.setTaskId(job.taskId());
        e.setJobId(job.id());
        e.setRunId(job.runId());
        e.setOrg(org);
        e.setRepo(repo);
        e.setJobName(job.name());
        e.setStatus(job.status());
        e.setHtmlUrl(job.htmlUrl());
        if (run != null) {
            e.setRunTitle(run.title());
            e.setEvent(run.event());
            e.setRef(run.prettyRef());
            e.setCommitSha(run.commitSha());
            e.setPrNumber(prNumber(run));
        }
        e.setStartedAt(job.startedAt() != null ? job.startedAt() : now);
        applyHead(e, head, runnersByUuid);
        return e;
    }

    /**
     * Sets the runner and the start of {@code e} from {@code head}, when it is the head of the log of {@code e}'s task.
     *
     * @return whether the runner is now known
     */
    public static boolean applyHead(JobExecution e, Optional<LogHead> head, Map<String, RunnerRow> runnersByUuid) {
        if (head.isEmpty() || head.get().taskId() != e.getTaskId()) {
            return e.getRunnerId() != null;
        }
        e.setStartedAt(head.get().at());
        RunnerRow runner = runnersByUuid.get(head.get().runnerUuid());
        if (runner != null) {
            e.setRunnerId(runner.getId());
        }
        return e.getRunnerId() != null;
    }

    /** The pull request a run builds: an event {@code pull_request*} and a ref {@code #298}; null otherwise. */
    public static Integer prNumber(ForgeRun run) {
        if (run == null || run.event() == null || !run.event().startsWith("pull_request") || run.prettyRef() == null) {
            return null;
        }
        Matcher m = PR_REF.matcher(run.prettyRef().strip());
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }

    /** The repository of a job, from its page {@code https://forge/<owner>/<repo>/actions/runs/…}. */
    public static Optional<String> repoOf(String htmlUrl) {
        if (htmlUrl == null) {
            return Optional.empty();
        }
        try {
            String path = URI.create(htmlUrl).getPath();
            List<String> segments = List.of(path == null ? new String[0] : path.replaceFirst("^/", "").split("/"));
            return segments.size() > 2 && "actions".equals(segments.get(2)) ? Optional.of(segments.get(1))
                    : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
