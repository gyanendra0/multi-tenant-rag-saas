"""
Database access for the worker.

CRITICAL — Row Level Security: the worker connects as `rag_app` (a non-superuser),
so RLS applies to it exactly like the Java backend. Before touching any
tenant-scoped table we run:

    SELECT set_config('app.current_tenant', <tenant_id>, true);

inside the SAME transaction, so the INSERTs satisfy the tenant policy's
USING/WITH CHECK. `true` = transaction-local, auto-cleared at commit/rollback.
"""

from __future__ import annotations

import psycopg
from pgvector.psycopg import register_vector

from app.chunking import Chunk
from app.config import settings


def _connect() -> psycopg.Connection:
    conn = psycopg.connect(settings.pg_conninfo)
    # Teach psycopg how to adapt Python lists <-> pgvector types.
    register_vector(conn)
    return conn


def update_status_scoped(tenant_id: str, document_id: str, status: str,
                         error_message: str | None = None,
                         chunk_count: int | None = None) -> None:
    """Set document status within the tenant's RLS scope."""
    with _connect() as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT set_config('app.current_tenant', %s, true)",
                        (tenant_id,))
            if chunk_count is None:
                cur.execute(
                    "UPDATE document SET status = %s, error_message = %s, "
                    "updated_at = now() WHERE id = %s",
                    (status, error_message, document_id),
                )
            else:
                cur.execute(
                    "UPDATE document SET status = %s, error_message = %s, "
                    "chunk_count = %s, updated_at = now() WHERE id = %s",
                    (status, error_message, chunk_count, document_id),
                )
        conn.commit()


def replace_chunks(tenant_id: str, document_id: str,
                   chunks: list[Chunk], embeddings: list[list[float]]) -> int:
    """
    Idempotently store a document's chunks + embeddings.

    Deletes any existing chunks for the document (safe re-run) then bulk-inserts
    the new ones. All within one RLS-scoped transaction. Returns the count.
    """
    assert len(chunks) == len(embeddings), "chunks/embeddings length mismatch"

    with _connect() as conn:
        register_vector(conn)
        with conn.cursor() as cur:
            cur.execute("SELECT set_config('app.current_tenant', %s, true)",
                        (tenant_id,))

            # Idempotency: clear prior chunks for this document first.
            cur.execute("DELETE FROM document_chunk WHERE document_id = %s",
                        (document_id,))

            # Bulk insert. tenant_id is set explicitly and must match the GUC
            # (RLS WITH CHECK enforces it).
            cur.executemany(
                "INSERT INTO document_chunk "
                "(tenant_id, document_id, chunk_index, content, token_count, embedding) "
                "VALUES (%s, %s, %s, %s, %s, %s)",
                [
                    (tenant_id, document_id, c.index, c.content, c.token_count, e)
                    for c, e in zip(chunks, embeddings)
                ],
            )
        conn.commit()
    return len(chunks)
