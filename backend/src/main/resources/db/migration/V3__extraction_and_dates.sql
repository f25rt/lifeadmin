-- V3: AI-extracted fields, important dates, and the idempotency ledger.
-- Every extracted value carries its source (OCR/AI/DERIVED/USER) and a confidence, so the UI can
-- surface uncertainty and mark derived values (spec §8/§25). Nothing is auto-trusted.

-- ---------------------------------------------------------------------------
-- Extracted field: a key/value pulled from the document. rawValue keeps the pre-normalization
-- string for verification (G6). verified flips true when the user confirms/edits it.
-- ---------------------------------------------------------------------------
CREATE TABLE extracted_field (
    id           UUID PRIMARY KEY,
    document_id  UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    field_name   VARCHAR(64)  NOT NULL,
    field_value  VARCHAR(2000),
    raw_value    VARCHAR(2000),
    confidence   NUMERIC(4,3),
    source       VARCHAR(16)  NOT NULL,
    verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   VARCHAR(255),
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_field_source CHECK (source IN ('OCR','AI','DERIVED','USER'))
);
CREATE INDEX idx_field_document ON extracted_field (document_id);

-- ---------------------------------------------------------------------------
-- Important date: explicit (printed) or derived (calculated) date of interest. sourceRuleId links a
-- derived date to the VerifiedSourceRule it came from (added in a later migration; nullable now).
-- ---------------------------------------------------------------------------
CREATE TABLE important_date (
    id             UUID PRIMARY KEY,
    document_id    UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    date_type      VARCHAR(32)  NOT NULL,
    date_value     DATE         NOT NULL,
    source         VARCHAR(16)  NOT NULL,
    confidence     NUMERIC(4,3),
    source_rule_id UUID,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     VARCHAR(255),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_date_source CHECK (source IN ('OCR','AI','DERIVED','USER')),
    CONSTRAINT ck_date_type CHECK (date_type IN
        ('EXPIRATION','RENEWAL','PAYMENT_DEADLINE','CONTRACT_START','CONTRACT_END',
         'WARRANTY_EXPIRATION','INSPECTION','APPOINTMENT','PURCHASE','OTHER'))
);
CREATE INDEX idx_date_document ON important_date (document_id);
CREATE INDEX idx_date_value ON important_date (date_value);

-- ---------------------------------------------------------------------------
-- Processed event: idempotency ledger for the async workers (G11). A worker inserts the eventId
-- before acting; a duplicate delivery short-circuits. Pruned periodically (retention note in docs).
-- ---------------------------------------------------------------------------
CREATE TABLE processed_event (
    id            UUID PRIMARY KEY,
    event_id      VARCHAR(64)  NOT NULL UNIQUE,
    event_type    VARCHAR(64)  NOT NULL,
    document_id   UUID,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Suggested action: AI-proposed next step for a document (spec §10). Presented as suggestions, not
-- guaranteed facts. done flag lets the user check them off.
-- ---------------------------------------------------------------------------
CREATE TABLE suggested_action (
    id           UUID PRIMARY KEY,
    document_id  UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    label        VARCHAR(255) NOT NULL,
    done         BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order   INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_action_document ON suggested_action (document_id);
