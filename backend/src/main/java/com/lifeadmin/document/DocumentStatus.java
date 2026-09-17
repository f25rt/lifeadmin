package com.lifeadmin.document;

/**
 * Document lifecycle (spec §19). UPLOADED → PROCESSING → REVIEW_REQUIRED → ACTIVE, with ARCHIVED and
 * FAILED as terminal states. Phase 2 only sets UPLOADED (and ARCHIVED via PATCH); the rest are
 * driven by the async pipeline in Phase 3.
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    REVIEW_REQUIRED,
    ACTIVE,
    ARCHIVED,
    FAILED
}
