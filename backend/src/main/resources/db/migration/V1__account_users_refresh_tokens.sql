-- V1: Foundation — account (ownership root), users, subscription (plan/limits), refresh tokens.
-- Ownership is modeled at the ACCOUNT level from day 1 (GAPS_AND_DECISIONS G12) so families /
-- business workspaces can be added later without a data migration.

-- ---------------------------------------------------------------------------
-- Account: the ownership root. In the MVP one account has exactly one user (OWNER).
-- ---------------------------------------------------------------------------
CREATE TABLE account (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(255),
    version     BIGINT       NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- User: a login account belonging to an Account. Email is globally unique.
-- ---------------------------------------------------------------------------
CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    account_id    UUID         NOT NULL REFERENCES account (id),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'OWNER',
    timezone      VARCHAR(64)  NOT NULL DEFAULT 'Asia/Manila',
    country       VARCHAR(8)   NOT NULL DEFAULT 'PH',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(255),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    VARCHAR(255),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER'))
);
CREATE INDEX idx_user_account ON app_user (account_id);

-- ---------------------------------------------------------------------------
-- Subscription: single source of truth for plan + limits (one row per account).
-- No denormalized plan on account (avoids drift). Billing integration is out of MVP scope (D1).
-- ---------------------------------------------------------------------------
CREATE TABLE subscription (
    id                     UUID PRIMARY KEY,
    account_id             UUID        NOT NULL UNIQUE REFERENCES account (id),
    plan                   VARCHAR(16) NOT NULL DEFAULT 'FREE',
    document_limit         INT         NOT NULL DEFAULT 5,
    people_limit           INT         NOT NULL DEFAULT 1,
    ai_extraction_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    current_period_end     TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             VARCHAR(255),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by             VARCHAR(255),
    version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_subscription_plan CHECK (plan IN ('FREE', 'PERSONAL_PRO', 'FAMILY', 'BUSINESS'))
);

-- ---------------------------------------------------------------------------
-- Refresh token: stateful refresh tokens (G18). Only a HASH of the token is stored.
-- Logout revokes the row; refresh rotates (revoke old + insert new).
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_token (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES app_user (id),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_active ON refresh_token (user_id) WHERE revoked_at IS NULL;
