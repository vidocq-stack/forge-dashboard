package io.vidocq.tools.dashboard.forge;

import io.vidocq.tools.dashboard.domain.CiState;

import java.util.List;

/** The combined status of a ref, and the statuses it combines. */
public record CombinedStatus(CiState state, List<StatusContext> contexts) {

    public CombinedStatus {
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
    }
}
