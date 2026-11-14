package io.vidocq.tools.dashboard.live;

import java.time.Instant;
import java.util.List;

/**
 * What the dashboard shows of one organisation, immutable: replaced as a whole by {@link LiveState#update}. Each loop
 * replaces its own part and keeps the other.
 */
public record OrgSnapshot(String org, List<RepoView> repos, List<RunnerView> runners, List<JobView> waiting,
                          Instant updatedAt) {

    public OrgSnapshot {
        repos = List.copyOf(repos);
        runners = List.copyOf(runners);
        waiting = List.copyOf(waiting);
    }

    /** Nothing known yet; {@code updatedAt} is null. */
    public static OrgSnapshot empty(String org) {
        return new OrgSnapshot(org, List.of(), List.of(), List.of(), null);
    }

    public OrgSnapshot withRepos(List<RepoView> repos, Instant at) {
        return new OrgSnapshot(org, repos, runners, waiting, at);
    }

    public OrgSnapshot withRunners(List<RunnerView> runners, List<JobView> waiting, Instant at) {
        return new OrgSnapshot(org, repos, runners, waiting, at);
    }
}
