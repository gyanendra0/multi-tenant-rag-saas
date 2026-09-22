package com.tenantrag.backend.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Membership linking a {@code user} to a {@code tenant} with a role.
 * RLS-protected: the {@code tenant_id} must match {@code app.current_tenant}.
 */
@Entity
@Table(name = "tenant_member")
public class TenantMember {

    @EmbeddedId
    private TenantMemberId id;

    @Column(nullable = false)
    private String role;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    public TenantMember() {
    }

    public TenantMember(TenantMemberId id, String role, UUID invitedBy) {
        this.id = id;
        this.role = role;
        this.invitedBy = invitedBy;
    }

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = OffsetDateTime.now();
        }
    }

    public TenantMemberId getId() {
        return id;
    }

    public void setId(TenantMemberId id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public void setInvitedBy(UUID invitedBy) {
        this.invitedBy = invitedBy;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}
