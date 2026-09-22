package com.tenantrag.backend.ingest;

/**
 * Phase 7 (step 4) — the payload published to RabbitMQ after a successful
 * upload. The Python worker consumes this from the {@code documents.ingest}
 * queue and runs the ingestion pipeline (download → parse → chunk → embed →
 * store).
 *
 * <p>The field names are the JSON keys the worker expects
 * ({@code documentId}, {@code tenantId}, {@code storageKey}, {@code mimeType},
 * {@code filename}) — keep them in sync with {@code worker/app/main.py}.
 */
public record IngestMessage(
        String documentId,
        String tenantId,
        String storageKey,
        String mimeType,
        String filename
) {
}
