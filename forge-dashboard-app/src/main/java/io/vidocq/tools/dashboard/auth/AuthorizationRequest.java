package io.vidocq.tools.dashboard.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** The URL that sends the browser to CodeFloe's consent page: authorization code flow with PKCE S256. */
public final class AuthorizationRequest {

    static final String SCOPE = "openid profile email groups";

    private AuthorizationRequest() {
    }

    public static URI build(OidcSettings s, String state, String nonce, String codeChallenge) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("response_type", "code");
        q.put("client_id", s.clientId());
        q.put("redirect_uri", s.redirectUri().toString());
        q.put("scope", SCOPE);
        q.put("state", state);
        q.put("nonce", nonce);
        q.put("code_challenge", codeChallenge);
        q.put("code_challenge_method", "S256");
        return URI.create(s.endpoint("/login/oauth/authorize") + "?" + form(q));
    }

    static String form(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
