package io.vidocq.tools.dashboard.auth;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationRequestTest {

    static OidcSettings settings() {
        return new OidcSettings(URI.create("https://codefloe.com"), "cid", "test-secret",
                URI.create("https://forge-dashboard.vidocq.dev"), Set.of("Vidocq:owners", "Vidocq:devs"),
                Set.of("Vidocq:owners"));
    }

    static Map<String, String> query(URI uri) {
        Map<String, String> q = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            q.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return q;
    }

    @Test
    void authorizationUrlCarriesAllParameters() {
        var uri = AuthorizationRequest.build(settings(), "S", "N", "C");
        assertEquals("https://codefloe.com/login/oauth/authorize", uri.getScheme() + "://" + uri.getHost() + uri.getPath());
        var q = query(uri);
        assertEquals("code", q.get("response_type"));
        assertEquals("cid", q.get("client_id"));
        assertEquals("https://forge-dashboard.vidocq.dev/oauth2/callback", q.get("redirect_uri"));
        assertEquals("openid profile email groups", q.get("scope"));
        assertEquals("S256", q.get("code_challenge_method"));
        assertEquals("C", q.get("code_challenge"));
        assertEquals("S", q.get("state"));
        assertEquals("N", q.get("nonce"));
    }

    @Test
    void redirectUriIgnoresATrailingSlash() {
        var s = new OidcSettings(URI.create("https://codefloe.com/"), "cid", "x", URI.create("http://localhost:8080/"),
                Set.of(), Set.of());
        assertEquals(URI.create("http://localhost:8080/oauth2/callback"), s.redirectUri());
        assertEquals("https://codefloe.com/login/oauth/authorize",
                AuthorizationRequest.build(s, "S", "N", "C").toString().split("\\?")[0]);
    }
}
