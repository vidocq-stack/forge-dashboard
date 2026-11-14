package io.vidocq.tools.dashboard.forge;

import io.vidocq.tools.dashboard.domain.CiState;

/** One commit status, such as {@code pr-validate / build (pull_request)}. */
public record StatusContext(String context, CiState state, String targetUrl) {
}
