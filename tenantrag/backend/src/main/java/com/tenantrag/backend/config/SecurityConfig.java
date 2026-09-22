package com.tenantrag.backend.config;

import com.tenantrag.backend.auth.TenantContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

@Configuration
public class SecurityConfig {

    private final byte[] jwtSecret;

    public SecurityConfig(@Value("${app.jwt.secret}") String secret) {
        this.jwtSecret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            TenantContextFilter tenantContextFilter) throws Exception {

        http
            // Disable CSRF for our stateless REST API
            .csrf(AbstractHttpConfigurer::disable)

            // Disable default browser authentication mechanisms
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )

            // Phase 3.15 — validate JWTs as an OAuth2 Resource Server
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter()))
            );

        // Phase 3.19 — copy tenant_id from the validated JWT into TenantContext.
        // Must run AFTER the JWT is validated. For OAuth2 resource server, the
        // JWT is authenticated by BearerTokenAuthenticationFilter, so we place
        // our filter right after it (NOT after UsernamePasswordAuthenticationFilter,
        // which runs earlier and would leave the SecurityContext empty here).
        http.addFilterAfter(
                tenantContextFilter,
                BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Phase 3.15 — HS256 decoder using the same shared secret as {@code JwtService}.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(jwtSecret, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    /**
     * Maps the custom {@code role} claim (e.g. "ORG_OWNER") to a Spring authority
     * {@code ROLE_ORG_OWNER}, enabling {@code hasRole(...)} checks later.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes =
                new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities =
                    scopes.convert(jwt);
            String role = jwt.getClaimAsString("role");
            if (role != null && !role.isBlank()) {
                authorities.add(new org.springframework.security.core.authority
                        .SimpleGrantedAuthority("ROLE_" + role));
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}