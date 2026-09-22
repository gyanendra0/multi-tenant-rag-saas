package com.tenantrag.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke every still-active token for a user (used on logout-all / password
     * change). Sets revoked_at on rows not already revoked.
     */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now " +
            "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("now") OffsetDateTime now);
}
