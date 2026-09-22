package com.tenantrag.backend.auth;

import com.tenantrag.backend.auth.dto.AuthResponse;
import com.tenantrag.backend.auth.dto.LoginRequest;
import com.tenantrag.backend.auth.dto.RegisterRequest;
import com.tenantrag.backend.auth.dto.UserResponse;
import com.tenantrag.backend.tenant.Tenant;
import com.tenantrag.backend.tenant.TenantDatabaseService;
import com.tenantrag.backend.tenant.TenantMember;
import com.tenantrag.backend.tenant.TenantMemberId;
import com.tenantrag.backend.tenant.TenantMemberRepository;
import com.tenantrag.backend.tenant.TenantRepository;
import com.tenantrag.backend.user.User;
import com.tenantrag.backend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Phase 3.11 — Registration.
 *
 * <p>Atomically creates a {@link User}, a {@link Tenant}, and an ORG_OWNER
 * {@link TenantMember}. The whole flow is a single transaction so any failure
 * rolls everything back.
 *
 * <p>Because {@code tenant_member} is protected by PostgreSQL RLS, the
 * membership insert is performed only after {@code app.current_tenant} has
 * been set to the freshly created tenant within this same transaction.
 */
@Service
public class AuthService {

    private static final String ROLE_ORG_OWNER = "ORG_OWNER";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final TenantDatabaseService tenantDatabaseService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            TenantDatabaseService tenantDatabaseService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {

        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.tenantDatabaseService = tenantDatabaseService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase();
        String slug = request.tenantSlug().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new AuthConflictException(
                    "Email already registered: " + email);
        }

        if (tenantRepository.existsBySlug(slug)) {
            throw new AuthConflictException(
                    "Tenant slug already in use: " + slug);
        }

        // 1) Create the user with a BCrypt-hashed password.
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setActive(true);
        user = userRepository.save(user);

        // 2) Create the tenant.
        Tenant tenant = new Tenant();
        tenant.setName(request.tenantName().trim());
        tenant.setSlug(slug);
        tenant.setPlan("free");
        tenant.setStatus("active");
        tenant = tenantRepository.save(tenant);

        // 3) Establish tenant context so the RLS-protected insert is allowed.
        tenantDatabaseService.setTenant(tenant.getId());

        // 4) Create the ORG_OWNER membership.
        TenantMemberId memberId =
                new TenantMemberId(tenant.getId(), user.getId());
        TenantMember member =
                new TenantMember(memberId, ROLE_ORG_OWNER, null);
        tenantMemberRepository.save(member);

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                ROLE_ORG_OWNER
        );
    }

    /**
     * Phase 3.13 — Login. Verifies credentials, resolves the user's active
     * tenant + role, and issues a JWT access token.
     *
     * <p>Runs in one transaction so that {@code app.current_user_id} (set via
     * {@link TenantDatabaseService#setCurrentUser}) is visible to the
     * RLS-guarded membership query (see the {@code member_self_access} policy in
     * the V3 migration).
     *
     * <p>All failure modes throw the same {@link InvalidCredentialsException}
     * with a generic message to avoid revealing whether an email is registered.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {

        String email = request.email().trim().toLowerCase();

        // 1) Look up the user ("user" table is not RLS-protected).
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Invalid email or password"));

        // 2) Verify the password against the stored BCrypt hash.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // 3) Reject inactive accounts.
        if (!user.isActive()) {
            throw new InvalidCredentialsException("Account is disabled");
        }

        // 4) Establish user context so the self-access RLS policy allows the
        //    membership read, then load memberships.
        tenantDatabaseService.setCurrentUser(user.getId());
        List<TenantMember> memberships =
                tenantMemberRepository.findByIdUserIdOrderByJoinedAtAsc(user.getId());

        if (memberships.isEmpty()) {
            // A valid user with no tenant should not normally happen.
            throw new InvalidCredentialsException(
                    "User is not a member of any tenant");
        }

        // 5) Pick the oldest membership as the active tenant for this token.
        TenantMember active = memberships.get(0);
        UUID tenantId = active.getId().getTenantId();
        String role = active.getRole();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Active tenant not found"));

        // 6) Issue the access token.
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), tenantId, role);

        // 7) Issue a rotating refresh token (stored hashed).
        String refreshToken = refreshTokenService.issue(user.getId(), tenantId);

        UserResponse profile = new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                role
        );

        return AuthResponse.bearer(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenTtlSeconds(),
                profile
        );
    }

    /**
     * Phase 3.16 — Exchange a valid refresh token for a new access token, and
     * rotate the refresh token (old one is revoked, a new one is returned).
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {

        RefreshTokenService.RotationResult rotated =
                refreshTokenService.rotate(rawRefreshToken);

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new InvalidCredentialsException(
                        "User no longer exists"));

        if (!user.isActive()) {
            throw new InvalidCredentialsException("Account is disabled");
        }

        UUID tenantId = rotated.tenantId();

        // Re-read the current role for this user+tenant (it may have changed).
        tenantDatabaseService.setCurrentUser(user.getId());
        TenantMember member = tenantMemberRepository
                .findByIdUserIdAndIdTenantId(user.getId(), tenantId)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "User is no longer a member of this tenant"));
        String role = member.getRole();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Active tenant not found"));

        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), tenantId, role);

        UserResponse profile = new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                role
        );

        return AuthResponse.bearer(
                accessToken,
                rotated.rawToken(),
                jwtService.getAccessTokenTtlSeconds(),
                profile
        );
    }

    /**
     * Phase 3.16 — Logout: revoke the presented refresh token. The access token
     * remains valid until it expires (it is stateless) — keep TTL short.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }
}
