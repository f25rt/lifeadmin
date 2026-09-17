package com.lifeadmin.document;

/** Document categories (spec §7). Persisted as {@code EnumType.STRING}; matches the DB CHECK. */
public enum DocumentType {
    PASSPORT,
    DRIVERS_LICENSE,
    VEHICLE_REGISTRATION,
    INSURANCE,
    WARRANTY,
    RECEIPT,
    CONTRACT,
    PROPERTY_DOCUMENT,
    GOVERNMENT_DOCUMENT,
    LICENSE,
    CERTIFICATE,
    BILL,
    SUBSCRIPTION,
    OTHER
}
