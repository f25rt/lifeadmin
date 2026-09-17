package com.lifeadmin.security.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A stateful refresh token (maps to {@code refresh_token}, V1). Only a **hash** of the token is
 * stored — never the raw value (G18). Logout sets {@link #revokedAt}; refresh rotates by revoking
 * the presented token and inserting a new row. A token is usable when not revoked and not expired.
 *
 * <p>Not {@code Auditable}: this is security bookkeeping with its own explicit lifecycle columns.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isActive(final Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
