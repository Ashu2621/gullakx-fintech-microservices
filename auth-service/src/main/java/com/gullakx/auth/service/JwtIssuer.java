package com.gullakx.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Issues and verifies access tokens.
 *
 * HS256 with a shared secret, not RS256 with a published JWKS. That is a
 * deliberate trade for a system this size: both services are deployed together
 * and already share a database password, so a second key-distribution mechanism
 * would add operational surface without removing a trust relationship that
 * already exists.
 *
 * It stops being the right answer the moment a third party needs to verify a
 * token without being able to mint one - at that point the shared secret is
 * exactly the problem, and this class becomes RS256 with a JWKS endpoint. The
 * verify path below is already algorithm-agnostic from the caller's side, so
 * that change is contained here.
 */
@Component
public class JwtIssuer {

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;

    public JwtIssuer(
            @Value("${gullakx.jwt.secret}") String secret,
            @Value("${gullakx.jwt.ttl-minutes:60}") long ttlMinutes,
            @Value("${gullakx.jwt.issuer:gullakx-auth}") String issuer) {

        byte[] material = secret.getBytes(StandardCharsets.UTF_8);
        if (material.length < 32) {
            // HS256 keys shorter than the hash output weaken the signature, and
            // a short secret is the kind of thing that ships from a tutorial
            // and never gets revisited. Refuse to start instead.
            throw new IllegalStateException(
                    "gullakx.jwt.secret must be at least 32 bytes; got " + material.length);
        }
        this.key = Keys.hmacShaKeyFor(material);
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.issuer = issuer;
    }

    public String issue(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** @throws JwtException if the token is forged, expired or from another issuer. */
    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Map<String, Object> describe() {
        return Map.of("issuer", issuer, "ttlMinutes", ttl.toMinutes());
    }
}
