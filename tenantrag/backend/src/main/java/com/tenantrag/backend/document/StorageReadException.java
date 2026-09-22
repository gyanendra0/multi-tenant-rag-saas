package com.tenantrag.backend.document;

/**
 * Thrown when the uploaded file's bytes cannot be read from the request.
 * Handled as a 400 by the global exception handler.
 */
public class StorageReadException extends RuntimeException {
    public StorageReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
