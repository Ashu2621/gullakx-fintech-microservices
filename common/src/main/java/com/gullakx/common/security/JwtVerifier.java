package com.gullakx.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Verifies an access token. Shared by the issuer and by every service that
 * accepts one, so there is exactly one definition of "valid" in the system —
 * two copies drift, and the drift shows up as a service accepting a token the
 * issuer never meant to mint.
 */
public class JwtVerifier {

    private final SecretKey key;
    private final String issuer;

    public JwtVerifier(String secret, String issuer) {
        byte[] material = secret.getBytes(StandardCharsets.UTF_8);
        if (material.length < 32) {
            // A short HS256 key weakens the signature, and it is exactly the
            // kind of value that arrives from a tutorial and is never revisited.
            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes; got " + material.length);
        }
        this.key = Keys.hmacShaKeyFor(material);
        this.issuer = issuer;
    }

    SecretKey key() {
        return key;
    }

    String issuer() {
        return issuer;
    }

    /**
     * @throws JwtException if the signature is wrong, the token has expired, or
     *                      it was minted by a different issuer.
     */
    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** The user id a token speaks for, or null if the token is not usable. */
    public Long subjectOf(String token) {
        try {
            return Long.valueOf(verify(token).getSubject());
        } catch (JwtException | NumberFormatException rejected) {
            return null;
        }
    }
}
