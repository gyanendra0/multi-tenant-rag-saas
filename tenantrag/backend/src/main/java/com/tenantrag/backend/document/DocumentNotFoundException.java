package com.tenantrag.backend.document;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a document does not exist for the current tenant. Because RLS
 * hides other tenants' rows, a cross-tenant id looks identical to a missing id —
 * which is the desired behaviour (no information leak about other tenants).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(Object id) {
        super("Document not found: " + id);
    }
}
