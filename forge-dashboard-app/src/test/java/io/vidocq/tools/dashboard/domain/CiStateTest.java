package io.vidocq.tools.dashboard.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CiStateTest {

    @Test
    void mapsForgejoStates() {
        assertEquals(CiState.SUCCESS, CiState.fromForgejo("success"));
        assertEquals(CiState.FAILURE, CiState.fromForgejo("failure"));
        assertEquals(CiState.FAILURE, CiState.fromForgejo("error"));
        assertEquals(CiState.FAILURE, CiState.fromForgejo("warning"));
        assertEquals(CiState.PENDING, CiState.fromForgejo("pending"));
        assertEquals(CiState.NONE, CiState.fromForgejo(null));
        assertEquals(CiState.NONE, CiState.fromForgejo(""));
        assertEquals(CiState.NONE, CiState.fromForgejo("skipped"));
    }
}
