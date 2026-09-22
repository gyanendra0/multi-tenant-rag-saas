# Architecture

This document explains how the pieces fit together.

## Services (the moving parts)

| Service | Tech | Port | Responsibility |
|---------|------|------|----------------|
| **backend** | Java 21, Spring Boot 3.5 | 8080 | REST API, auth, tenant isolation, retrieval, chat orchestration |
| **rag-service** | Python 3.12, FastAPI | 8100 | Embeddings (local) + LLM answers (via Groq) |
| **worker** | Python 3.12 | — | Consumes ingest jobs; parses/chunks/embeds documents |
| **web** | Angular (Phase 9) | 4200 | Browser UI (planned) |
| **PostgreSQL** | Postgres 16 + pgvector | 5432 | Metadata + vector store; RLS enforces isolation |
| **MinIO** | S3‑compatible | 9000/9001 | Stores the actual uploaded files |
| **RabbitMQ** | 3.13 | 5672/15672 | Queues async ingestion jobs |
| **Redis** | 7 | 6379 | Ingestion job status / cache |

> **Why split backend (Java) and rag-service (Python)?** ML libraries live in
> Python; the secure, tenant‑aware business logic lives in Java. The backend calls
> the rag-service over HTTP for embeddings and LLM answers, but **all
> tenant‑isolated database access stays in the backend** so RLS always applies.

## Data flow 1 — Upload & ingestion (async)

```
① POST /api/documents (file)         Backend
        store bytes ─────────────►   MinIO
        insert row (status=PENDING)   Postgres (RLS)
        publish job ─────────────►   RabbitMQ  (documents.ingest)
        return 202 Accepted

② Worker consumes the job:
        download file  ◄──────────   MinIO
        parse → clean → chunk (512 tokens / 64 overlap)
        embed chunks   ──────────►   rag-service /embed  (384‑dim vectors)
        store chunks   ──────────►   Postgres document_chunk (RLS)
        set status=READY             (progress mirrored to Redis)
```

## Data flow 2 — Chat (synchronous RAG)

```
POST /api/chat  { question }         Backend
   embed question ───────────────►   rag-service /embed/query
   cosine search (RLS) ───────────►  Postgres document_chunk  → top‑K chunks
   build grounded prompt (context + question)
   generate answer ──────────────►   rag-service /generate → Groq LLM
   persist conversation + messages (RLS) with citations
   return { answer, citations, conversationId }
```

## Why this is "multi‑tenant safe"

Every tenant‑scoped table has **Row‑Level Security**. Before any query, the
backend runs `set_config('app.current_tenant', <tenantId>, true)` inside the
transaction. PostgreSQL then only shows/accepts rows for that tenant — even for
raw SQL like the vector search. See [tenant-isolation.md](tenant-isolation.md).

## Embeddings & vectors

- Model: `BAAI/bge-small-en-v1.5` (**384‑dim**, local, free, offline).
- Column: `document_chunk.embedding halfvec(384)`.
- Index: HNSW with `halfvec_cosine_ops` (cosine similarity via the `<=>` operator).
- Query score = `1 - cosine_distance` (higher = more similar).

## LLM

- Any **OpenAI‑compatible** API. Default: **Groq**.
- Primary model `openai/gpt-oss-120b`, fallback `llama-3.3-70b-versatile`.
- Configured entirely in the rag-service (`LLM_*` env vars) — the backend never
  holds the API key.
