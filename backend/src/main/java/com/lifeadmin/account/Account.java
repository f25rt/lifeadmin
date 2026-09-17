package com.lifeadmin.account;

import java.util.UUID;

import com.lifeadmin.common.persistence.Auditable;

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
 * The ownership root (maps to {@code account}, V1). Every other owned resource references an
 * account. In the MVP an account has exactly one {@link AppUser} with role {@code OWNER}; families
 * and business workspaces add members later without a schema migration (G12).
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
public class Account extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;
}
