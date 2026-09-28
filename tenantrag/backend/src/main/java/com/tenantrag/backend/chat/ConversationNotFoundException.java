package com.tenantrag.backend.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Conversation doesn't exist for the current tenant+user. Cross-tenant (RLS) and
 * other-user ids look identical to missing ids → 404, no information leak.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(Object id) {
        super("Conversation not found: " + id);
    }
}
