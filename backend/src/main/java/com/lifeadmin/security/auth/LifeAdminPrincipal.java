package com.lifeadmin.security.auth;

import java.util.UUID;

/**
 * The authenticated principal placed in the security context. Carries the identifiers business code
 * needs — notably {@code accountId}, the ownership key used by every resource check (G12).
 */
public record LifeAdminPrincipal(UUID userId, UUID accountId, String email, String role) {
}
