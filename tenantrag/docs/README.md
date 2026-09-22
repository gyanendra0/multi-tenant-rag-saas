# Multi‑Tenant RAG SaaS — Documentation

Welcome! This folder documents the project so a newcomer can understand **what it
is**, **how it's built**, and **how to run it**.

## What is this project?

A **multi‑tenant Retrieval‑Augmented Generation (RAG) SaaS**. Multiple
organizations ("tenants") can:

1. **Sign up** and get their own isolated space.
2. **Upload documents** (PDF, DOCX, TXT, Markdown, HTML, CSV).
3. **Ask questions** and get answers **grounded in their own documents**, with
   citations back to the source.

The key promise is **tenant isolation**: one tenant can *never* see another
tenant's data. This is enforced at the **database** level with PostgreSQL Row‑Level
Security (RLS), not just in application code — so even a bug in the app can't leak
data across tenants.

## Documentation index

| Doc | What's inside |
|-----|---------------|
| [`architecture.md`](architecture.md) | The big picture: services, data flow, diagrams |
| [`getting-started.md`](getting-started.md) | Step‑by‑step: run the whole thing locally |
| [`api.md`](api.md) | Every HTTP endpoint with example requests |
| [`tenant-isolation.md`](tenant-isolation.md) | How RLS keeps tenants separate (the security core) |
| [`phases.md`](phases.md) | What was built in each phase, in order |
| [`../infra/README.md`](../infra/README.md) | Infrastructure: Postgres, MinIO, RabbitMQ, Redis |

## The 30‑second mental model

```
You (browser)
   │  ①  login → JWT
   ▼
Backend (Java / Spring Boot)  ──────────────┐
   │  ②  upload doc → store file + DB row    │  ⑤  chat: embed question,
   │                                         │      search YOUR chunks (RLS),
   ▼                                         │      ask the LLM, return answer
MinIO (files)   Postgres (metadata+vectors)  │
   │                                         ▼
   │  ③  queue "ingest" job            rag-service (Python)
   ▼                                    - embeddings (local)
RabbitMQ ──► Worker (Python) ─ parse ─ chunk ─ embed ─► store vectors
                                                        - LLM answer (Groq)
```

If you're brand new, start with **[getting-started.md](getting-started.md)**.
