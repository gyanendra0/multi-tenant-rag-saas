package com.tenantrag.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * A chat request. {@code question} is required; {@code conversationId} is
 * optional — omit it to start a new conversation, or pass an existing one to
 * continue it.
 */
public record ChatRequest(
        @NotBlank String question,
        UUID conversationId
) {
}
