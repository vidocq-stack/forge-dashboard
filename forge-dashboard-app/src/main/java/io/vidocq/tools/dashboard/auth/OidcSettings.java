package io.vidocq.tools.dashboard.auth;

import java.net.URI;
import java.util.Set;

/**
 * The CodeFloe OIDC client: the provider, this application's credentials, its public URL, and who may come in.
 * {@link #toString()} never shows the secret.
 */
public record OidcSettings(URI issuer, String clientId, String clientSecret, URI publicUrl, Set<String> allowedGroups,
                           Set<String> adminGroups) {

    public OidcSettings {
        allowedGroups = Set.copyOf(allowedGroups);
        adminGroups = Set.copyOf(adminGroups);
    }

    public URI redirectUri() {
        return URI.create(withoutTrailingSlash(publicUrl) + "/oauth2/callback");
    }

    URI endpoint(String path) {
        return URI.create(withoutTrailingSlash(issuer) + path);
    }

    private static String withoutTrailingSlash(URI uri) {
        String s = uri.toString();
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    @Override
    public String toString() {
        return "OidcSettings[issuer=" + issuer + ", clientId=" + clientId + ", clientSecret=***, publicUrl=" + publicUrl
                + ", allowedGroups=" + allowedGroups + ", adminGroups=" + adminGroups + "]";
    }
}
