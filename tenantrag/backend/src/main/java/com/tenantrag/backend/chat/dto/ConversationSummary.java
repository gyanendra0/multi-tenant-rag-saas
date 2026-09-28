package com.tenantrag.backend.chat.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Sidebar entry for a conversation. */
public record ConversationSummary(
        UUID id,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
