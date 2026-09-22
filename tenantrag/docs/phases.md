# Build Phases

The project was built incrementally. Each phase is independently testable.

| Phase | Title | Status |
|-------|-------|--------|
| 1 | Project scaffold & structure | ✅ |
| 2 | Database schema + Flyway | ✅ |
| 3 | Auth (JWT) + tenant model | ✅ |
| 4 | Tenant isolation (RLS) | ✅ |
| 5 | Document metadata APIs | ✅ |
| 6 | File storage (MinIO) + upload | ✅ |
| 7 | Async ingestion (RabbitMQ + worker) | ✅ |
| 8 | RAG retrieval + chat with citations | ✅ |
| 9 | Angular frontend | ⏳ next |
| 10 | Dockerize + production polish | ⏳ |

---

## Phase 3 — Auth & tenants
- Register creates a tenant + owner user; login issues JWT access/refresh tokens.
- `SecurityConfig` + `JwtService` + `CustomUserDetailsService`.
- Tenant id is embedded in the JWT and drives isolation downstream.

## Phase 4 — Tenant isolation (RLS)
- Non‑superuser `rag_app` role, `FORCE ROW LEVEL SECURITY`, per‑transaction
  `app.current_tenant` GUC. Verified with an 8/8 isolation test matrix.
- See [tenant-isolation.md](tenant-isolation.md).

## Phase 5 — Document APIs
- CRUD for document metadata, all tenant‑scoped.

## Phase 6 — File storage
- MinIO (S3‑compatible) via Docker; bucket `rag-documents`.
- Upload stores bytes in MinIO + a metadata row in Postgres.

## Phase 7 — Async ingestion
- Upload publishes a `documents.ingest` message to RabbitMQ and returns **202**.
- Python **worker** downloads, parses, chunks (512/64), embeds via rag-service,
  writes chunks with RLS, and flips status `PENDING → READY`.

## Phase 8 — RAG chat
- `/api/rag/search`: RLS‑scoped cosine top‑K retrieval.
- `/api/chat`: embed question → retrieve → grounded prompt → LLM answer →
  persist conversation/messages with citations.
- LLM via Groq (OpenAI‑compatible), primary `gpt-oss-120b`, fallback `llama‑3.3‑70b`.
- **Key fixes:** `SimpleClientHttpRequestFactory` (avoid `Expect: 100-continue`
  body drop), and citations stored as JSON via `?::jsonb` (postgresql driver is
  runtime‑scoped, so `PGobject` isn't available at compile time).

## Phase 9 — Frontend (next)
- Angular (standalone) + Angular Material.
- Planned in 3 steps: (1) scaffold + auth, (2) documents, (3) chat.

## Phase 10 — Productionize
- Dockerize backend/rag-service/worker, compose everything, env‑based config.
