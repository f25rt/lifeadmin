package com.lifeadmin.account;

import java.util.UUID;

import com.lifeadmin.common.persistence.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A login account (maps to {@code app_user}, V1). Belongs to an {@link Account}; {@code accountId}
 * is the ownership key used by every resource check. Named {@code AppUser} to avoid clashing with
 * the many "user" concepts and the SQL reserved word.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private UserRole role = UserRole.OWNER;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Manila";

    @Column(name = "country", nullable = false, length = 8)
    private String country = "PH";

    /** Soft-lock: a disabled user cannot log in or refresh tokens. Set by a SUPER_ADMIN (V8). */
    @Column(name = "disabled", nullable = false)
    private boolean disabled = false;
}
