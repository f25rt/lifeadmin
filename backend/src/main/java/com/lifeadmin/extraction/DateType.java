package com.lifeadmin.extraction;

/** Kinds of important dates (spec §9). Persisted as {@code EnumType.STRING}; matches the DB CHECK. */
public enum DateType {
    EXPIRATION,
    RENEWAL,
    PAYMENT_DEADLINE,
    CONTRACT_START,
    CONTRACT_END,
    WARRANTY_EXPIRATION,
    INSPECTION,
    APPOINTMENT,
    PURCHASE,
    OTHER
}
