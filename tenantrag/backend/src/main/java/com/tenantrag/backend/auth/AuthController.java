package com.tenantrag.backend.auth;

import com.tenantrag.backend.auth.dto.AuthResponse;
import com.tenantrag.backend.auth.dto.LoginRequest;
import com.tenantrag.backend.auth.dto.RegisterRequest;
import com.tenantrag.backend.auth.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Auth endpoints.
 *
 * <p>The <b>refresh token</b> travels only in an httpOnly cookie (never in the
 * JSON body), so JavaScript — and therefore XSS — can't read it. The cookie is
 * scoped to {@code /api/auth} and {@code SameSite=Strict}, so the browser sends
 * it only to refresh/logout and never on cross-site requests. The short-lived
 * access token is still returned in the body and kept in memory by the client.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final boolean cookieSecure;
    private final long refreshTtlSeconds;

    public AuthController(
            AuthService authService,
            @Value("${app.auth.refresh-cookie-secure:false}") boolean cookieSecure,
            @Value("${app.jwt.refresh-token-ttl-seconds}") long refreshTtlSeconds) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    /**
     * Phase 3.11 — Registration.
     * Creates User + Tenant + ORG_OWNER membership atomically.
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Phase 3.13 — Login.
     * Verifies credentials and returns a JWT access token + profile.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return withRefreshCookie(authService.login(request));
    }

    /**
     * Phase 3.16 — Refresh. Reads the refresh token from the httpOnly cookie,
     * rotates it (new cookie set), and returns a new access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException("Missing refresh token");
        }
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    /**
     * Phase 3.16 — Logout. Revokes the cookie's refresh token (if any) and
     * clears the cookie. Idempotent: always 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {

        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    // -- helpers -------------------------------------------------------------

    /** Move the refresh token from the body into an httpOnly cookie. */
    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse auth) {
        ResponseCookie cookie = refreshCookie(
                auth.refreshToken(), Duration.ofSeconds(refreshTtlSeconds));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(auth.withoutRefreshToken());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
