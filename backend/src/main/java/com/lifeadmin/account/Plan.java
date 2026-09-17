package com.lifeadmin.account;

/** Subscription plans (spec §26). Persisted as {@code EnumType.STRING}; matches the DB CHECK. */
public enum Plan {
    FREE,
    PERSONAL_PRO,
    FAMILY,
    BUSINESS
}
