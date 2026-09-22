package com.tenantrag.backend.rag;

/** Raised when the internal rag-service call fails or returns an unexpected body. */
public class RagServiceException extends RuntimeException {

    public RagServiceException(String message) {
        super(message);
    }

    public RagServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
