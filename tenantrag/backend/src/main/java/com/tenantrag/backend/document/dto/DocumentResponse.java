package com.tenantrag.backend.document.dto;

import com.tenantrag.backend.document.Document;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Client-facing view of a document. Deliberately omits {@code storageKey}
 * (an internal object-store path that clients should never see).
 */
public record DocumentResponse(
        UUID id,
        UUID uploadedBy,
        String title,
        String filename,
        String mimeType,
        long sizeBytes,
        String status,
        String errorMessage,
        int chunkCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(),
                d.getUploadedBy(),
                d.getTitle(),
                d.getFilename(),
                d.getMimeType(),
                d.getSizeBytes(),
                d.getStatus(),
                d.getErrorMessage(),
                d.getChunkCount(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
