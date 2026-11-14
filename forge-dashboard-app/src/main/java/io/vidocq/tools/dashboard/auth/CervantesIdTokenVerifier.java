package io.vidocq.tools.dashboard.auth;

import io.vidocq.cervantes.api.JwtValidationException;
import io.vidocq.cervantes.api.JwtValidator;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;

/**
 * The id_token checked by Cervantes against {@code mp.jwt.verify.*}: CodeFloe's keys (RS256), issuer, audience (the
 * client id) and expiry.
 */
public final class CervantesIdTokenVerifier implements IdTokenVerifier {

    private final JwtValidator validator;

    public CervantesIdTokenVerifier(JwtValidator validator) {
        this.validator = validator;
    }

    @Override
    public VerifiedIdentity verify(String idToken) {
        JsonWebToken jwt;
        try {
            jwt = validator.validate(idToken);
        } catch (JwtValidationException e) {
            throw new IllegalArgumentException("Invalid id_token: " + e.getMessage(), e);
        }
        String login = jwt.getClaim("preferred_username");
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("The id_token has no preferred_username");
        }
        Set<String> groups = jwt.getClaim("groups");
        return new VerifiedIdentity(login, string(jwt.getClaim("name")), string(jwt.getClaim("picture")),
                groups == null ? Set.of() : groups, string(jwt.getClaim("nonce")));
    }

    private static String string(Object claim) {
        return claim instanceof String s ? s : null;
    }
}
