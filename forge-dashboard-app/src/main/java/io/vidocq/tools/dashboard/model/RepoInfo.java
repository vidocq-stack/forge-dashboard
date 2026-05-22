package io.vidocq.tools.dashboard.model;

import java.time.Instant;
import java.util.List;

/**
 * Vue projetee d'un repo Forgejo affichee dans le dashboard.
 *
 * <p>Les {@code specs} sont derivees des topics du repo (convention :
 * un topic du type {@code jakarta-rest-4.0} ou {@code microprofile-config-3.1}
 * est interprete comme une declaration d'implementation de spec).
 */
public record RepoInfo(
        String owner,
        String name,
        String fullName,
        String description,
        boolean archived,
        boolean priv,
        String htmlUrl,
        List<String> topics,
        List<String> specs,
        Instant updatedAt,
        CommitInfo lastCommit,
        int openPullRequests,
        BuildStatus latestBuild) {
}
