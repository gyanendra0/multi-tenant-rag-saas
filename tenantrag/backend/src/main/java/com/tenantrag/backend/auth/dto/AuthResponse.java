package com.tenantrag.backend.auth.dto;

/**
 * Authentication response carrying issued tokens plus the user profile.
 * {@code refreshToken} may be null until Phase 3.16 is implemented.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {
    public static AuthResponse bearer(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            UserResponse user) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresInSeconds,
                user
        );
    }
}
