package com.tenantrag.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a registration/login request conflicts with existing data,
 * e.g. an email or tenant slug already in use.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AuthConflictException extends RuntimeException {

    public AuthConflictException(String message) {
        super(message);
    }
}
