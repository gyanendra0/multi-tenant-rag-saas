package com.tenantrag.backend.storage;

/**
 * Thrown when an object-store operation (upload/delete) fails.
 * Handled as a 500 by the global exception handler.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
