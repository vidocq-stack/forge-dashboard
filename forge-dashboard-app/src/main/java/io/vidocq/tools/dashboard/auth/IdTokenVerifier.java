package io.vidocq.tools.dashboard.auth;

/** Checks an id_token (signature, issuer, audience, expiry) and reads the identity it carries. */
public interface IdTokenVerifier {

    /** @throws IllegalArgumentException when the token is not valid */
    VerifiedIdentity verify(String idToken);
}
