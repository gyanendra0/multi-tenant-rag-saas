package com.tenantrag.backend.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMemberRepository
        extends JpaRepository<TenantMember, TenantMemberId> {

    Optional<TenantMember> findByIdUserIdAndIdTenantId(
            UUID userId,
            UUID tenantId
    );

    /**
     * All memberships for a user, ordered by join time so the oldest tenant is
     * chosen as the default "active" tenant at login. Relies on the
     * {@code member_self_access} RLS policy (V3) plus
     * {@code app.current_user_id} being set.
     */
    List<TenantMember> findByIdUserIdOrderByJoinedAtAsc(UUID userId);
}
