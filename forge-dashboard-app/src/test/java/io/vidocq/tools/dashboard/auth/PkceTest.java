package io.vidocq.tools.dashboard.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PkceTest {

    @Test
    void pkceMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                Pkce.challengeS256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
        assertTrue(Pkce.newVerifier().matches("[A-Za-z0-9_-]{43,128}"));
    }

    @Test
    void verifiersAreRandom() {
        assertNotEquals(Pkce.newVerifier(), Pkce.newVerifier());
        assertTrue(Pkce.randomToken().matches("[A-Za-z0-9_-]{22,}"));
    }
}
