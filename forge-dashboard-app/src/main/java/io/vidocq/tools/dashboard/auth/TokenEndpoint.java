package io.vidocq.tools.dashboard.auth;

import java.net.URI;

/** The provider's token endpoint: exchanges an authorization code. Fails with an unchecked exception. */
public interface TokenEndpoint {

    TokenResponse exchange(String code, String codeVerifier, URI redirectUri);
}
