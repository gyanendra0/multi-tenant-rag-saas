package com.tenantrag.backend.auth.dto;

import java.util.UUID;

/**
 * Public view of a user + their tenant + role. Never exposes the password hash.
 */
public record UserResponse(
        UUID userId,
        String email,
        String fullName,
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        String role
) {
}
