package io.vidocq.tools.dashboard.auth;

import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.vidocq.tools.dashboard.auth.AuthorizationRequestTest.settings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallbackHandlerTest {

    private static final LoginState SAVED = new LoginState("S", "N", "V", "/ci");

    private final List<String> exchanges = new ArrayList<>();
    private VerifiedIdentity identity = new VerifiedIdentity("yblazart", "Yann", "https://codefloe.com/avatar/y",
            Set.of("Vidocq:owners", "Vidocq"), "N");
    private RuntimeException exchangeFailure;
    private RuntimeException verifyFailure;

    private final TokenEndpoint tokens = (code, verifier, redirect) -> {
        exchanges.add(code + "|" + verifier + "|" + redirect);
        if (exchangeFailure != null) {
            throw exchangeFailure;
        }
        return new TokenResponse("id-token", "access-token");
    };
    private final IdTokenVerifier verifier = idToken -> {
        if (verifyFailure != null) {
            throw verifyFailure;
        }
        assertEquals("id-token", idToken);
        return identity;
    };
    private final CallbackHandler handler = new CallbackHandler(settings(), tokens, verifier);

    @Test
    void successfulCallbackBuildsAdminUser() {
        var result = assertInstanceOf(CallbackResult.Success.class, handler.handle(Map.of("code", "x", "state", "S"), SAVED));

        assertEquals("/ci", result.returnTo());
        assertEquals("yblazart", result.user().login());
        assertEquals("Yann", result.user().name());
        assertTrue(result.user().admin());
        assertEquals(List.of("x|V|https://forge-dashboard.vidocq.dev/oauth2/callback"), exchanges);
    }

    @Test
    void devIsNotAdmin() {
        identity = new VerifiedIdentity("dev", "Dev", null, Set.of("Vidocq:devs"), "N");
        var result = assertInstanceOf(CallbackResult.Success.class, handler.handle(Map.of("code", "x", "state", "S"), SAVED));
        assertFalse(result.user().admin());
    }

    @Test
    void missingLoginStateRestartsOnce() {
        assertInstanceOf(CallbackResult.Restart.class, handler.handle(Map.of("code", "x", "state", "S"), null));
    }

    @Test
    void stateMismatchRestarts() {
        assertInstanceOf(CallbackResult.Restart.class, handler.handle(Map.of("code", "x", "state", "T"), SAVED));
        assertInstanceOf(CallbackResult.Restart.class, handler.handle(Map.of("code", "x"), SAVED));
        assertTrue(exchanges.isEmpty());
    }

    @Test
    void missingCodeRestarts() {
        assertInstanceOf(CallbackResult.Restart.class, handler.handle(Map.of("state", "S"), SAVED));
    }

    @Test
    void providerErrorIsDenied() {
        var denied = assertInstanceOf(CallbackResult.Denied.class,
                handler.handle(Map.of("error", "access_denied", "state", "S"), SAVED));
        assertEquals("CodeFloe a refusé : access_denied", denied.reason());
    }

    @Test
    void userOutsideGroupsIsDenied() {
        identity = new VerifiedIdentity("someone", "Someone", null, Set.of("VidocqTools"), "N");
        var denied = assertInstanceOf(CallbackResult.Denied.class, handler.handle(Map.of("code", "x", "state", "S"), SAVED));
        assertTrue(denied.reason().startsWith("Accès réservé aux groupes "), denied.reason());
        assertTrue(denied.reason().contains("Vidocq:devs"), denied.reason());
    }

    @Test
    void nonceMismatchIsDenied() {
        identity = new VerifiedIdentity("yblazart", "Yann", null, Set.of("Vidocq:owners"), "other");
        assertInstanceOf(CallbackResult.Denied.class, handler.handle(Map.of("code", "x", "state", "S"), SAVED));
    }

    @Test
    void invalidIdTokenIsDenied() {
        verifyFailure = new IllegalArgumentException("bad signature");
        assertInstanceOf(CallbackResult.Denied.class, handler.handle(Map.of("code", "x", "state", "S"), SAVED));
    }

    @Test
    void failedExchangeIsDenied() {
        exchangeFailure = new UncheckedIOException(new IOException("connect timed out"));
        assertInstanceOf(CallbackResult.Denied.class, handler.handle(Map.of("code", "x", "state", "S"), SAVED));
    }

    @Test
    void openRedirectIsNeutralised() {
        for (String target : List.of("https://evil.example", "//evil.example", "/\\evil.example", "ci", "")) {
            var saved = new LoginState("S", "N", "V", target);
            var result = assertInstanceOf(CallbackResult.Success.class,
                    handler.handle(Map.of("code", "x", "state", "S"), saved));
            assertEquals("/", result.returnTo(), target);
        }
    }

    @Test
    void deniedReasonIsEscapedOnThePage() {
        String page = ForbiddenPage.html("CodeFloe a refusé : <script>alert(1)</script>");
        assertFalse(page.contains("<script>"), page);
        assertTrue(page.contains("&lt;script&gt;"), page);
        assertTrue(page.contains("href=\"/\""), page);
    }

    @Test
    void redirectUriIsTheCallback() {
        assertEquals(URI.create("https://forge-dashboard.vidocq.dev/oauth2/callback"), settings().redirectUri());
    }
}
