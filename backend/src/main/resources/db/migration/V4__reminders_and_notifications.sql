-- V4: Reminders, notifications, and the ShedLock table.
-- Reminder timing is timezone-aware (G16): we store the local date + the user's IANA timezone and a
-- precomputed UTC instant the scheduler queries by. Notification uniqueness prevents double-send (G2).

-- ---------------------------------------------------------------------------
-- Reminder: fires at scheduledForUtc; produces a notification (and/or email). The title is a
-- creation-time snapshot (e.g. "Insurance expires in 60 days").
-- ---------------------------------------------------------------------------
CREATE TABLE reminder (
    id                  UUID PRIMARY KEY,
    account_id          UUID         NOT NULL REFERENCES account (id),
    document_id         UUID         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    important_date_id   UUID         REFERENCES important_date (id) ON DELETE SET NULL,
    title               VARCHAR(255) NOT NULL,
    reminder_local_date DATE         NOT NULL,
    user_timezone       VARCHAR(64)  NOT NULL,
    scheduled_for_utc   TIMESTAMPTZ  NOT NULL,
    channel             VARCHAR(16)  NOT NULL DEFAULT 'IN_APP',
    status              VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULED',
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(255),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_reminder_channel CHECK (channel IN ('IN_APP','EMAIL')),
    CONSTRAINT ck_reminder_status CHECK (status IN ('SCHEDULED','SENT','CANCELLED','FAILED'))
);
CREATE INDEX idx_reminder_account ON reminder (account_id);
CREATE INDEX idx_reminder_document ON reminder (document_id);
-- The scheduler's hot query: due and not yet sent.
CREATE INDEX idx_reminder_due ON reminder (scheduled_for_utc) WHERE sent_at IS NULL AND status = 'SCHEDULED';

-- ---------------------------------------------------------------------------
-- Notification (in-app). read_flag drives the unread badge. The (reminder_id,type,scheduled_for)
-- uniqueness guarantees a reminder is delivered at most once even across scheduler runs/instances.
-- ---------------------------------------------------------------------------
CREATE TABLE notification (
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES app_user (id),
    reminder_id    UUID         REFERENCES reminder (id) ON DELETE CASCADE,
    type           VARCHAR(24)  NOT NULL,
    title          VARCHAR(160) NOT NULL,
    body           VARCHAR(1000),
    read_flag      BOOLEAN      NOT NULL DEFAULT FALSE,
    reference_type VARCHAR(32),
    reference_id   UUID,
    scheduled_for  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_type CHECK (type IN ('REMINDER','SYSTEM','NEEDS_ATTENTION'))
);
CREATE INDEX idx_notification_recipient ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_unread ON notification (user_id) WHERE read_flag = FALSE;
-- At most one reminder notification per (user, reminder). Guarantees idempotent delivery across
-- scheduler runs/instances while still allowing every user on an account to be notified.
CREATE UNIQUE INDEX uq_notification_reminder ON notification (user_id, reminder_id, type)
    WHERE reminder_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- ShedLock table (distributed lock for the scheduler).
-- ---------------------------------------------------------------------------
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
