package com.tenantrag.backend.chat.dto;

import java.util.List;
import java.util.UUID;

/**
 * A chat response: the grounded answer, the sources it was built from, and the
 * conversation id (new or existing) so the client can continue the thread.
 */
public record ChatResponse(
        UUID conversationId,
        UUID messageId,
        String answer,
        List<Citation> citations
) {
}
