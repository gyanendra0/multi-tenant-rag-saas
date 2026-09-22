package com.tenantrag.backend.auth;

import com.tenantrag.backend.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Phase 3.19 — Populate {@link TenantContext} from the authenticated JWT.
 *
 * <p>Runs after Spring Security has validated the bearer token. If the current
 * authentication is a {@link Jwt}, its {@code tenant_id} claim is copied into the
 * request-scoped {@link TenantContext} so downstream services (and, via
 * {@code TenantTransactionService}, PostgreSQL RLS) operate on the right tenant.
 *
 * <p>The context is always cleared in a {@code finally} block to avoid leaking a
 * tenant id onto a pooled thread that later serves another request.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    static final String TENANT_CLAIM = "tenant_id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        boolean tenantSet = false;
        try {
            Authentication auth =
                    SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                String tenantId = jwt.getClaimAsString(TENANT_CLAIM);
                if (tenantId != null && !tenantId.isBlank()) {
                    TenantContext.setTenantId(UUID.fromString(tenantId));
                    tenantSet = true;
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            if (tenantSet) {
                TenantContext.clear();
            }
        }
    }
}
