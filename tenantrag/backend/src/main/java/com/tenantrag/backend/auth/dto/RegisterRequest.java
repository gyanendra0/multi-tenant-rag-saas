package com.tenantrag.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Creates a User + Tenant + ORG_OWNER membership.
 */
public record RegisterRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        @NotBlank
        @Size(max = 200)
        String fullName,

        @NotBlank
        @Size(max = 200)
        String tenantName,

        @NotBlank
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "slug must be lowercase alphanumeric words separated by hyphens"
        )
        @Size(min = 2, max = 100)
        String tenantSlug
) {
}
