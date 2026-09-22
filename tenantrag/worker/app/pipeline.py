"""
The ingestion pipeline: download -> parse -> chunk -> embed -> store.

Called by the RabbitMQ consumer for each `documents.ingest` message:
    {documentId, tenantId, storageKey, mimeType, filename}
"""

from __future__ import annotations

import logging

from app import db, embeddings, jobstatus
from app.chunking import chunk_text
from app.config import settings
from app.parsing import parse
from app.storage import download_bytes

log = logging.getLogger("worker.pipeline")


def _embed_in_batches(texts: list[str]) -> list[list[float]]:
    out: list[list[float]] = []
    size = settings.embed_batch_size
    for start in range(0, len(texts), size):
        batch = texts[start:start + size]
        out.extend(embeddings.embed_documents(batch))
    return out


def ingest_document(document_id: str, tenant_id: str, storage_key: str,
                    mime_type: str, filename: str) -> dict:
    """Run the full pipeline for one document. Raises on failure."""
    log.info("Ingest start doc=%s tenant=%s key=%s", document_id, tenant_id,
             storage_key)

    db.update_status_scoped(tenant_id, document_id, "PROCESSING")
    jobstatus.set_job(document_id, "PROCESSING", 10)

    try:
        # 1) Download bytes from MinIO.
        data = download_bytes(storage_key)
        jobstatus.set_job(document_id, "PROCESSING", 25)

        # 2) Parse to text.
        text = parse(data, mime_type, filename)
        jobstatus.set_job(document_id, "PROCESSING", 40)

        # 3) Chunk.
        chunks = chunk_text(text)
        if not chunks:
            # Nothing extractable — mark READY with 0 chunks (not an error).
            db.update_status_scoped(tenant_id, document_id, "READY",
                                    chunk_count=0)
            jobstatus.set_job(document_id, "READY", 100)
            log.info("Ingest done (empty) doc=%s", document_id)
            return {"document_id": document_id, "chunks": 0}

        # 4) Embed all chunk texts (batched).
        vectors = _embed_in_batches([c.content for c in chunks])
        jobstatus.set_job(document_id, "PROCESSING", 80)

        # 5) Store chunks + embeddings (RLS-scoped, idempotent).
        count = db.replace_chunks(tenant_id, document_id, chunks, vectors)

        # 6) Finalize.
        db.update_status_scoped(tenant_id, document_id, "READY",
                                chunk_count=count)
        jobstatus.set_job(document_id, "READY", 100)
        log.info("Ingest done doc=%s chunks=%d", document_id, count)
        return {"document_id": document_id, "chunks": count}

    except Exception as exc:  # noqa: BLE001 - we want to record any failure
        log.exception("Ingest FAILED doc=%s", document_id)
        db.update_status_scoped(tenant_id, document_id, "FAILED",
                                error_message=str(exc)[:500])
        jobstatus.set_job(document_id, "FAILED", 100, error=str(exc)[:500])
        raise
