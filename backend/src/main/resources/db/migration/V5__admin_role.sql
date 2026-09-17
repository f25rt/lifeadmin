-- V5: Platform admin role. Adds SUPER_ADMIN (cross-account administrator) to the allowed user roles.
-- The admin user itself is seeded at startup by AdminSeeder (uses the configured PasswordEncoder;
-- idempotent) rather than here, so we never hardcode a bcrypt hash or a password in SQL.

ALTER TABLE app_user DROP CONSTRAINT ck_user_role;
ALTER TABLE app_user ADD CONSTRAINT ck_user_role
    CHECK (role IN ('SUPER_ADMIN', 'OWNER', 'ADMIN', 'MEMBER', 'VIEWER'));
