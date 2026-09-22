package com.tenantrag.backend.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/**
 * Convenience accessors for the currently authenticated principal, read from the
 * validated JWT in the {@code SecurityContext}.
 *
 * <p>Tenant resolution deliberately lives in {@code TenantContext} (populated by
 * {@code TenantContextFilter}); this helper is for the <em>user</em> identity
 * (e.g. to stamp {@code uploaded_by}).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The authenticated user's id (JWT {@code sub}), if present. */
    public static Optional<UUID> id() {
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return Optional.of(UUID.fromString(sub));
            }
        }
        return Optional.empty();
    }

    /** The authenticated user's id, or throw if unauthenticated. */
    public static UUID requireId() {
        return id().orElseThrow(() -> new IllegalStateException(
                "No authenticated user in security context"));
    }
}
