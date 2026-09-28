package com.tenantrag.backend.chat.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** One persisted chat turn. {@code citations} is empty for user messages. */
public record MessageResponse(
        UUID id,
        String role,
        String content,
        List<Citation> citations,
        OffsetDateTime createdAt
) {
}
