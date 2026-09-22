package com.tenantrag.backend.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantDatabaseService {

    private final JdbcTemplate jdbcTemplate;

    public TenantDatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setTenant(UUID tenantId) {

        if (tenantId == null) {
            throw new IllegalArgumentException(
                    "Tenant ID cannot be null"
            );
        }

        jdbcTemplate.queryForObject(
                "SELECT set_config(" +
                        "'app.current_tenant', ?, true)",
                String.class,
                tenantId.toString()
        );
    }

    /**
     * Sets {@code app.current_user_id} for the current transaction. Used during
     * login so the {@code member_self_access} RLS policy (see V3 migration) lets
     * a user read only their own {@code tenant_member} rows before any tenant
     * context exists.
     *
     * <p>The third argument {@code true} makes the setting transaction-local, so
     * it is discarded at commit/rollback.
     */
    public void setCurrentUser(UUID userId) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID cannot be null"
            );
        }

        jdbcTemplate.queryForObject(
                "SELECT set_config(" +
                        "'app.current_user_id', ?, true)",
                String.class,
                userId.toString()
        );
    }
}