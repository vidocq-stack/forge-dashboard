package io.vidocq.tools.dashboard.model;

/**
 * Vue projetee d'une pull request — pour l'affichage du build status sur sa HEAD.
 */
public record PullRequestInfo(
        int number,
        String title,
        String headSha,
        String htmlUrl,
        BuildStatus build) {
}
