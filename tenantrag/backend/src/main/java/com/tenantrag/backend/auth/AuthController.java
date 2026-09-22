package com.tenantrag.backend.auth;

import com.tenantrag.backend.auth.dto.AuthResponse;
import com.tenantrag.backend.auth.dto.LoginRequest;
import com.tenantrag.backend.auth.dto.RefreshRequest;
import com.tenantrag.backend.auth.dto.RegisterRequest;
import com.tenantrag.backend.auth.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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

        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Phase 3.16 — Refresh. Exchanges a valid refresh token for a new access
     * token and rotates the refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request) {

        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /**
     * Phase 3.16 — Logout. Revokes the presented refresh token.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshRequest request) {

        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
