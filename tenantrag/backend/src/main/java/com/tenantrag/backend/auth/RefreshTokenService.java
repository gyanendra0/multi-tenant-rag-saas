package com.tenantrag.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Phase 3.16 — Refresh tokens (rotating, hashed, revocable).
 *
 * <p>Design:
 * <ul>
 *   <li>The raw token is a 256-bit random value, returned to the client ONCE.</li>
 *   <li>Only its SHA-256 hash is stored, so a DB leak reveals nothing usable.</li>
 *   <li>On refresh the presented token is rotated: the old row is revoked and a
 *       new token issued (rotation limits the window of a stolen token).</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final long refreshTokenTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @Value("${app.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds) {
        this.repository = repository;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    /**
     * Issues a new refresh token for a user+tenant and returns the RAW token
     * (the only time it is ever available in plaintext).
     */
    @Transactional
    public String issue(UUID userId, UUID tenantId) {
        String raw = generateRawToken();

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTenantId(tenantId);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(OffsetDateTime.now().plusSeconds(refreshTokenTtlSeconds));
        repository.save(token);

        return raw;
    }

    /**
     * Validates a presented raw token and rotates it: the matched token is
     * revoked and a brand-new one is issued. Returns the new raw token plus the
     * owning user/tenant so the caller can mint a fresh access token.
     *
     * @throws InvalidCredentialsException if the token is unknown, expired or revoked
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken existing = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Invalid refresh token"));

        OffsetDateTime now = OffsetDateTime.now();
        if (!existing.isUsable(now)) {
            throw new InvalidCredentialsException(
                    "Refresh token expired or revoked");
        }

        // Revoke the old token (rotation).
        existing.setRevokedAt(now);
        repository.save(existing);

        // Issue a replacement.
        String newRaw = issue(existing.getUserId(), existing.getTenantId());
        return new RotationResult(newRaw, existing.getUserId(), existing.getTenantId());
    }

    /** Revoke a single token (e.g. logout). No-op if it doesn't exist. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(OffsetDateTime.now());
                repository.save(token);
            }
        });
    }

    /** Revoke all active tokens for a user (e.g. logout-all / password change). */
    @Transactional
    public int revokeAllForUser(UUID userId) {
        return repository.revokeAllForUser(userId, OffsetDateTime.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        secureRandom.nextBytes(bytes);
        // URL-safe, no padding, so it's convenient in JSON/headers.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 hash of the raw token. A plain hash (not BCrypt) is correct here:
     * the token already has full 256-bit entropy, so slow hashing adds nothing;
     * we also need a deterministic value to look up by (BCrypt salts each hash).
     */
    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Result of a successful rotation. */
    public record RotationResult(String rawToken, UUID userId, UUID tenantId) {
    }
}
