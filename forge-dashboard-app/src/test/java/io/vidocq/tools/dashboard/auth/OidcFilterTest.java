package io.vidocq.tools.dashboard.auth;

import jakarta.servlet.annotation.WebFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcFilterTest {

    /**
     * The filter sits in front of {@code /VAADIN/push}: a chain is async only if every filter in it is, and Atmosphere
     * suspends each long-polling request with {@code startAsync} (else "async not supported", a 503 every 5 s).
     */
    @Test
    void filterSupportsAsyncForThePushChain() {
        assertTrue(OidcFilter.class.getAnnotation(WebFilter.class).asyncSupported());
    }
}
