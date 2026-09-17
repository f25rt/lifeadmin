package com.lifeadmin.account;

import java.time.Instant;
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
 * The single source of truth for an account's plan and limits (maps to {@code subscription}, V1;
 * one row per account). There is intentionally no denormalized plan on {@link Account} (avoids
 * drift). Billing/payment is out of MVP scope (D1); {@code currentPeriodEnd} is reserved for later.
 */
@Entity
@Table(name = "subscription")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 16)
    private Plan plan = Plan.FREE;

    @Column(name = "document_limit", nullable = false)
    private int documentLimit = 5;

    @Column(name = "people_limit", nullable = false)
    private int peopleLimit = 1;

    @Column(name = "ai_extraction_enabled", nullable = false)
    private boolean aiExtractionEnabled = true;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;
}
