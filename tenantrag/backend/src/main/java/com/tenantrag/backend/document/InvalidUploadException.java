package com.tenantrag.backend.document;

/**
 * Thrown when an uploaded file is missing or otherwise invalid.
 * Handled as a 400 by the global exception handler.
 */
public class InvalidUploadException extends RuntimeException {
    public InvalidUploadException(String message) {
        super(message);
    }
}
