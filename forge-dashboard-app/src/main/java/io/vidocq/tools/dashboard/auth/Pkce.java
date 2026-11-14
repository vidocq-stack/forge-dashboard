package io.vidocq.tools.dashboard.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Proof Key for Code Exchange (RFC 7636), and the random values of a login. */
public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {
    }

    /** 32 random bytes, base64url: 43 characters. */
    public static String newVerifier() {
        return random(32);
    }

    public static String challengeS256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return URL.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JDK", e);
        }
    }

    /** 16 random bytes, base64url: a {@code state} or a {@code nonce}. */
    public static String randomToken() {
        return random(16);
    }

    private static String random(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return URL.encodeToString(b);
    }
}
