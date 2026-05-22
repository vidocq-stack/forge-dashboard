package io.vidocq.tools.dashboard.model;

import java.util.Locale;

/**
 * Status combiné d'un commit, tel que rapporté par Forgejo
 * via {@code GET /repos/{owner}/{repo}/commits/{ref}/status}.
 *
 * @param state    état combiné (success / failure / pending / error / unknown)
 * @param count    nombre de statuts contribués sur le commit
 * @param summary  description courte du premier status (ex. « Successful in 24s »)
 * @param htmlUrl  lien absolu vers le run (target_url normalisé), ou {@code null}
 */
public record BuildStatus(State state, int count, String summary, String htmlUrl) {

    public static final BuildStatus NONE = new BuildStatus(State.NONE, 0, null, null);

    public enum State {
        SUCCESS, FAILURE, PENDING, ERROR, WARNING, NONE;

        public static State of(String s) {
            if (s == null) return NONE;
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "success" -> SUCCESS;
                case "failure", "failed" -> FAILURE;
                case "pending", "running", "queued" -> PENDING;
                case "error" -> ERROR;
                case "warning" -> WARNING;
                default -> NONE;
            };
        }

        public String icon() {
            return switch (this) {
                case SUCCESS -> "✓";   // ✓
                case FAILURE, ERROR -> "✗"; // ✗
                case PENDING -> "⋯";   // ⋯
                case WARNING -> "⚠";   // ⚠
                case NONE -> "—";       // —
            };
        }

        public String cssClass() {
            return switch (this) {
                case SUCCESS -> "ok";
                case FAILURE, ERROR -> "ko";
                case PENDING -> "pending";
                case WARNING -> "warn";
                case NONE -> "none";
            };
        }
    }
}
