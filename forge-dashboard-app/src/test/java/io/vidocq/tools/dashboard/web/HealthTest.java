package io.vidocq.tools.dashboard.web;

import io.vidocq.runtime.junit.VidocqApplication;
import io.vidocq.runtime.junit.VidocqTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@VidocqTest(properties = {"vaadin.productionMode=true", "dashboard.collect.enabled=false",
        "OIDC_CLIENT_ID=cid", "OIDC_CLIENT_SECRET=test-secret", "DASHBOARD_PUBLIC_URL=http://localhost:8080"})
class HealthTest {

    @Test
    void healthzAnswersOk(VidocqApplication app) {
        var r = app.get("/healthz");
        assertEquals(200, r.status(), app.log());
        assertEquals("OK", r.body().strip());
    }

    @Test
    void rootRedirectsToCodefloe(VidocqApplication app) {
        var r = app.get("/");
        assertEquals(302, r.status(), app.log());
        String location = r.header("Location").orElseThrow();
        assertTrue(location.startsWith("https://codefloe.com/login/oauth/authorize?"), location);
        assertTrue(location.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Foauth2%2Fcallback"), location);
    }

    @Test
    void sessionCookieIsSecureHttpOnlyLax(VidocqApplication app) {
        String cookie = app.get("/").header("Set-Cookie").orElseThrow();
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("Secure"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
    }

    /**
     * A Vaadin bootstrap or an XHR without login is refused. (A message, heartbeat or push poll goes on to Vaadin,
     * which answers that the session expired: see {@code OidcFilterTest}. Under {@code @VidocqTest} the Vaadin servlet
     * does not start, two instantiators being found, so Vaadin's answer is checked on the image.)
     */
    @Test
    void vaadinBootstrapOrXhrWithoutSessionIsUnauthorized(VidocqApplication app) {
        assertEquals(401, app.get("/?v-r=init&location=").status(), app.log());
        assertEquals(401, app.get("/", Map.of("X-Requested-With", "XMLHttpRequest")).status());
    }

    @Test
    void callbackWithoutLoginRedirectsToRoot(VidocqApplication app) {
        var r = app.get("/oauth2/callback?code=x&state=y", Map.of("Cookie", "JSESSIONID=stale"));
        assertEquals(302, r.status(), app.log());
        assertEquals("/", r.header("Location").orElseThrow());
    }

    @Test
    void callbackWithoutCookieExplainsInsteadOfLooping(VidocqApplication app) {
        var r = app.get("/oauth2/callback?code=x&state=y");
        assertEquals(400, r.status(), app.log());
        assertTrue(r.body().contains("cookie"), r.body());
    }

    @Test
    void logoutShowsAReconnectLink(VidocqApplication app) {
        var r = app.get("/oauth2/logout");
        assertEquals(200, r.status(), app.log());
        assertTrue(r.body().contains("Déconnecté"), r.body());
        assertTrue(r.body().contains("Se reconnecter"), r.body());
    }
}
