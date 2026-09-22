package com.tenantrag.backend.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Phase 3.14 — JWT generation.
 *
 * <p>Mints short-lived HS256 access tokens using Nimbus (from
 * {@code spring-security-oauth2-jose}). The token carries the identity and the
 * tenant/role so later phases can populate {@code TenantContext} straight from
 * the JWT (replacing the temporary {@code X-Tenant-Id} header).
 *
 * <p>Claims:
 * <ul>
 *   <li>{@code sub}       — user id</li>
 *   <li>{@code email}     — user email</li>
 *   <li>{@code tenant_id} — active tenant</li>
 *   <li>{@code role}      — membership role in that tenant</li>
 * </ul>
 */
@Service
public class JwtService {

    private final byte[] secret;
    private final long accessTokenTtlSeconds;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
            @Value("${app.jwt.issuer}") String issuer) {

        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256");
        }
        this.secret = key;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.issuer = issuer;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    /**
     * Builds and signs an access token for the given identity + active tenant.
     */
    public String generateAccessToken(
            UUID userId,
            String email,
            UUID tenantId,
            String role) {

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenTtlSeconds);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .claim("email", email)
                .claim("tenant_id", tenantId.toString())
                .claim("role", role)
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                claims);

        try {
            signedJwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }

        return signedJwt.serialize();
    }
}
