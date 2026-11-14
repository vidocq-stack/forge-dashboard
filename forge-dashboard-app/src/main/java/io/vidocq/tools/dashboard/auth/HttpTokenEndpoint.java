package io.vidocq.tools.dashboard.auth;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.annotation.JsonbProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CodeFloe's token endpoint over {@code java.net.http}. The client secret stays on the server; neither the request
 * nor the response body is ever logged.
 */
public final class HttpTokenEndpoint implements TokenEndpoint {

    private static final Jsonb JSONB = JsonbBuilder.create();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** The fields of the token response the dashboard reads. */
    public record RawTokens(@JsonbProperty("id_token") String idToken,
                            @JsonbProperty("access_token") String accessToken) {
    }

    private final OidcSettings settings;
    private final HttpClient http;

    public HttpTokenEndpoint(OidcSettings settings) {
        this.settings = settings;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public TokenResponse exchange(String code, String codeVerifier, URI redirectUri) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri.toString());
        form.put("client_id", settings.clientId());
        form.put("client_secret", settings.clientSecret());
        form.put("code_verifier", codeVerifier);
        HttpRequest request = HttpRequest.newBuilder(settings.endpoint("/login/oauth/access_token"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(AuthorizationRequest.form(form)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("CodeFloe token endpoint unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during the code exchange", e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("CodeFloe token endpoint answered " + response.statusCode());
        }
        RawTokens raw = JSONB.fromJson(response.body(), RawTokens.class);
        if (raw == null || raw.idToken() == null) {
            throw new IllegalStateException("CodeFloe returned no id_token (is the openid scope granted?)");
        }
        return new TokenResponse(raw.idToken(), raw.accessToken());
    }
}
