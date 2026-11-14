package io.vidocq.tools.dashboard.domain;

import java.util.Locale;

/** The CI state of a commit or a pull request, reduced from Forgejo's combined commit status. */
public enum CiState {
    SUCCESS, FAILURE, PENDING, NONE;

    /** Maps a Forgejo status ({@code success}, {@code failure}, {@code error}, {@code warning}, {@code pending}). */
    public static CiState fromForgejo(String state) {
        if (state == null) {
            return NONE;
        }
        return switch (state.strip().toLowerCase(Locale.ROOT)) {
            case "success" -> SUCCESS;
            case "failure", "error", "warning" -> FAILURE;
            case "pending" -> PENDING;
            default -> NONE;
        };
    }
}
