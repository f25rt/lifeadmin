package com.lifeadmin.security.auth;

import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Convenience accessor for the authenticated {@link LifeAdminPrincipal}. Business services depend on
 * this rather than touching {@link SecurityContextHolder} directly, keeping Spring Security details
 * out of the domain layer.
 */
@Component
public class CurrentUser {

    public Optional<LifeAdminPrincipal> principal() {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LifeAdminPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public LifeAdminPrincipal require() {
        return principal().orElseThrow(() -> new IllegalStateException("No authenticated principal in context"));
    }
}
