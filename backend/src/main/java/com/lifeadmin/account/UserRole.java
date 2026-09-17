package com.lifeadmin.account;

/**
 * Role of a user within their account. The MVP only creates {@link #OWNER}; the others exist so the
 * account/role model is future-proof for family/business workspaces (GAPS_AND_DECISIONS D5/G12).
 */
public enum UserRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER;

    /** Spring Security authority string, e.g. {@code ROLE_OWNER}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
