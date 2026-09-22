package com.tenantrag.backend.rag;

import java.util.UUID;

/**
 * A chunk returned by vector search, with its cosine similarity score and enough
 * metadata to build a citation.
 *
 * @param documentId the source document
 * @param chunkId    the chunk row id
 * @param chunkIndex position of the chunk within the document
 * @param title      the document's title (for display in citations)
 * @param content    the chunk text (used as LLM context)
 * @param score      cosine similarity in [0,1] (1 = identical direction)
 */
public record RetrievedChunk(
        UUID documentId,
        UUID chunkId,
        int chunkIndex,
        String title,
        String content,
        double score
) {
}
