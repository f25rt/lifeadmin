-- V8: Soft-lock a login. A SUPER_ADMIN can disable a user (blocks login + refresh) without deleting
-- data; re-enabling restores access. Ownership is at the account level (G12) but authentication is
-- per app_user, so the flag lives on app_user. Defaults to enabled (FALSE) for all existing rows.

ALTER TABLE app_user ADD COLUMN disabled BOOLEAN NOT NULL DEFAULT FALSE;
