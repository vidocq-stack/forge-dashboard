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

    /** A Vaadin bootstrap or an XHR without login is refused. */
    @Test
    void vaadinBootstrapOrXhrWithoutSessionIsUnauthorized(VidocqApplication app) {
        assertEquals(401, app.get("/?v-r=init&location=").status(), app.log());
        assertEquals(401, app.get("/", Map.of("X-Requested-With", "XMLHttpRequest")).status());
    }

    /**
     * A page whose session is gone (it still sends its old cookie: without any, Vaadin answers "Cookies disabled")
     * gets Vaadin's own "session expired", which reloads it and so starts the login, and no new session is opened.
     */
    @Test
    void vaadinMessageOrHeartbeatWithoutSessionGetsSessionExpired(VidocqApplication app) {
        Map<String, String> staleCookie = Map.of("Cookie", "JSESSIONID=stale");
        var uidl = app.request("POST", "/?v-r=uidl&v-uiId=0", staleCookie, "application/json",
                "{\"csrfToken\":\"x\",\"rpc\":[],\"syncId\":1}");
        assertEquals(200, uidl.status(), app.log());
        assertTrue(uidl.body().contains("\"sessionExpired\":true"), uidl.body());
        assertTrue(uidl.header("Set-Cookie").isEmpty(), uidl.header("Set-Cookie").orElse(""));

        var heartbeat = app.request("POST", "/?v-r=heartbeat&v-uiId=0", staleCookie, "text/plain", "");
        assertEquals(403, heartbeat.status());
        assertTrue(heartbeat.header("Set-Cookie").isEmpty(), heartbeat.header("Set-Cookie").orElse(""));
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
