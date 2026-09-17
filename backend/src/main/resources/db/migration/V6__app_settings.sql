-- V6: Admin-editable application settings (key/value). Used for upload rules (allowed file types,
-- max size, max PDF pages) and any future global toggles. Read at runtime with code-level defaults,
-- so an empty table behaves exactly like the previous hardcoded configuration.
CREATE TABLE app_setting (
    setting_key VARCHAR(64) PRIMARY KEY,
    value       TEXT         NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(255)
);
