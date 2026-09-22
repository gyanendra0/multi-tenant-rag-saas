package com.tenantrag.backend.chat.dto;

import java.util.UUID;

/**
 * A source reference attached to an assistant answer, so the UI can show
 * "where did this come from" and link back to the document/chunk.
 */
public record Citation(
        UUID documentId,
        UUID chunkId,
        int chunkIndex,
        String title,
        double score
) {
}
