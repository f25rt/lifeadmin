package com.lifeadmin.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * Issues and validates tokens.
 *
 * <ul>
 *   <li><b>Access token</b> — a short-lived signed JWT (HS256) carrying the user id (subject),
 *       account id, role, and email. Stateless; validated on every request.</li>
 *   <li><b>Refresh token</b> — a long, opaque, cryptographically-random string. Only its SHA-256
 *       hash is persisted ({@code RefreshToken}); the raw value is returned to the client once and
 *       never stored (G18).</li>
 * </ul>
 */
@Service
public class JwtTokenService {

    private final JwtProperties properties;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public JwtTokenService(final JwtProperties properties) {
        this.properties = properties;
        final var secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("lifeadmin.jwt.secret must be set and >= 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(final UUID userId, final UUID accountId, final String email, final String role) {
        final var now = Instant.now();
        final var expiry = now.plus(Duration.ofMinutes(properties.getAccessTtlMinutes()));
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(userId.toString())
                .claim("accountId", accountId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long getAccessTtlSeconds() {
        return Duration.ofMinutes(properties.getAccessTtlMinutes()).toSeconds();
    }

    /** Parses and validates a signed access token, returning its claims, or throws on invalid/expired. */
    public Claims parse(final String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // --- Refresh tokens (opaque) ---

    /** A new opaque refresh token (URL-safe base64 of 32 random bytes). Return to client once. */
    public String generateRefreshTokenValue() {
        final var bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(Duration.ofDays(properties.getRefreshTtlDays()));
    }

    /** SHA-256 hash (hex) of a refresh token value — what we persist, never the raw token. */
    public String hashRefreshToken(final String raw) {
        try {
            final var md = MessageDigest.getInstance("SHA-256");
            final var digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            final var sb = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (final Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
