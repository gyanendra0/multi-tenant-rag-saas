package com.tenantrag.backend.auth.dto;

/**
 * Authentication response carrying issued tokens plus the user profile.
 *
 * <p>Internally {@code refreshToken} is populated by the service; the controller
 * moves it into an httpOnly cookie and sends {@link #withoutRefreshToken()} to
 * the client, so the refresh token never appears in a JSON body.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user
) {
    /** Copy with the refresh token stripped (it's delivered via cookie). */
    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(accessToken, null, tokenType, expiresInSeconds, user);
    }

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
