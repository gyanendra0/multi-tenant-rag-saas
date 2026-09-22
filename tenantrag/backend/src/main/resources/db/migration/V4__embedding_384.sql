-- V4__embedding_384.sql
--
-- Phase 7 prep: switch the vector column to match our chosen embedding model.
--
-- We are using the local `sentence-transformers` model **BAAI/bge-small-en-v1.5**,
-- which outputs **384-dimensional** vectors. V1 sized the column for OpenAI's
-- `text-embedding-3-small` (1536 dims), so we must resize it. Every row's
-- embedding must have the SAME dimension, so this is a hard requirement — a
-- 384-dim vector cannot be stored in a halfvec(1536) column.
--
-- Why this is safe to change destructively here:
--   `document_chunk` has no real data yet (ingestion — the only writer — does
--   not exist until Phase 7). So there are no embeddings to preserve. If there
--   WERE data, we'd instead have to re-embed every chunk with the new model,
--   because vectors from different models are not comparable.
--
-- Why we must drop/recreate the index:
--   The HNSW index from V1 is bound to the column's type/dimension. You cannot
--   ALTER a column that an index depends on, so we DROP it, change the type,
--   then CREATE it again for the new dimension.
--
-- Why switch l2 -> cosine:
--   `bge-small-en-v1.5` is trained for **cosine similarity** retrieval. Using
--   `halfvec_cosine_ops` makes the index match how we'll actually query
--   (ORDER BY embedding <=> query). (`<=>` is cosine distance in pgvector.)

-- 1) Drop the old index that pins the column to halfvec(1536).
DROP INDEX IF EXISTS idx_chunk_embedding_hnsw;

-- 2) Resize the column to 384 dims.
--    USING NULL is not needed since the table is empty; a plain type change is
--    fine. If the table had rows, this would fail unless every vector already
--    had 384 dims — which is exactly what we want (forces a conscious re-embed).
ALTER TABLE document_chunk
    ALTER COLUMN embedding TYPE halfvec(384);

-- 3) Recreate the HNSW index for the new dimension, using cosine ops.
CREATE INDEX idx_chunk_embedding_hnsw
    ON document_chunk USING hnsw (embedding halfvec_cosine_ops);
