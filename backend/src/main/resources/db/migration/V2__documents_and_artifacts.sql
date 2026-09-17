-- V2: Documents, their stored artifacts, and the audit log.
-- Ownership is at the ACCOUNT level (G12). Binaries live in object storage; only keys/metadata
-- live here (spec §17). Person linkage is a nullable column now; the Person entity arrives later.

-- ---------------------------------------------------------------------------
-- Document: metadata + processing status. documentType/classificationConfidence are null until
-- Phase 3 (AI). title is the human display label (G19), defaulted server-side.
-- ---------------------------------------------------------------------------
CREATE TABLE document (
    id                         UUID PRIMARY KEY,
    account_id                 UUID         NOT NULL REFERENCES account (id),
    created_by_user_id         UUID         NOT NULL REFERENCES app_user (id),
    person_id                  UUID,                         -- nullable; FK added when Person exists
    title                      VARCHAR(255) NOT NULL,
    file_name                  VARCHAR(512) NOT NULL,
    mime_type                  VARCHAR(128) NOT NULL,
    file_size                  BIGINT       NOT NULL,
    document_type              VARCHAR(32),                  -- null until classified (Phase 3)
    classification_confidence  NUMERIC(4,3),
    status                     VARCHAR(24)  NOT NULL DEFAULT 'UPLOADED',
    failure_reason             VARCHAR(1000),
    source_locale_hint         VARCHAR(32),
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                 VARCHAR(255),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by                 VARCHAR(255),
    version                    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_document_status CHECK (status IN
        ('UPLOADED','PROCESSING','REVIEW_REQUIRED','ACTIVE','ARCHIVED','FAILED')),
    CONSTRAINT ck_document_type CHECK (document_type IS NULL OR document_type IN
        ('PASSPORT','DRIVERS_LICENSE','VEHICLE_REGISTRATION','INSURANCE','WARRANTY','RECEIPT',
         'CONTRACT','PROPERTY_DOCUMENT','GOVERNMENT_DOCUMENT','LICENSE','CERTIFICATE','BILL',
         'SUBSCRIPTION','OTHER'))
);
CREATE INDEX idx_document_account_status ON document (account_id, status);
CREATE INDEX idx_document_account_created ON document (account_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Document artifact: storage keys for the original / processed / thumbnail objects.
-- ---------------------------------------------------------------------------
CREATE TABLE document_artifact (
    id           UUID PRIMARY KEY,
    document_id  UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    kind         VARCHAR(16)  NOT NULL,
    storage_key  VARCHAR(512) NOT NULL,
    mime_type    VARCHAR(128) NOT NULL,
    file_size    BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_artifact_kind CHECK (kind IN ('ORIGINAL','PROCESSED','THUMBNAIL'))
);
CREATE INDEX idx_artifact_document ON document_artifact (document_id);

-- ---------------------------------------------------------------------------
-- Audit log (spec §18, G5): append-only record of sensitive actions. No account FK constraint so
-- deleting an account never erases its audit trail.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY,
    account_id     UUID,
    actor_user_id  UUID,
    action         VARCHAR(64)  NOT NULL,
    entity_type    VARCHAR(64)  NOT NULL,
    entity_id      VARCHAR(64),
    detail         VARCHAR(2000),
    correlation_id VARCHAR(64),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_account ON audit_log (account_id, occurred_at DESC);
CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id);
