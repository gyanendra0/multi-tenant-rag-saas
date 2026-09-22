# Infrastructure

This folder documents the infrastructure the project depends on. The three
Docker services live in [`../docker-compose.yml`](../docker-compose.yml).
PostgreSQL runs **natively** on the Mac (not in Docker) for stable RLS testing.

## Overview

| Component | Where it runs | Port(s) | Console / URL | Credentials |
|-----------|---------------|---------|---------------|-------------|
| PostgreSQL 16 + pgvector | Native (brew) | 5432 | — | app role `rag_app` / `1234` |
| MinIO | Docker | 9000 (API), 9001 (console) | http://localhost:9001 | `minioadmin` / `minioadmin` |
| RabbitMQ 3.13 | Docker | 5672 (AMQP), 15672 (console) | http://localhost:15672 | `guest` / `guest` |
| Redis 7 | Docker | 6379 | — | none |

## PostgreSQL

- Database: `rag_saas`.
- Extension: `vector` (pgvector) — provides `halfvec` type and `<=>` operator.
- Roles:
  - **superuser** = your macOS login role — used for admin only (bypasses RLS).
  - **`rag_app`** = the non‑superuser the backend & worker connect as. RLS
    applies to it. Password `1234` (dev only).
- Schema/migrations are managed by **Flyway**, run automatically by the backend
  at startup (`backend/src/main/resources/db/migration`).

Start / stop:
```bash
brew services start postgresql@16
brew services stop postgresql@16
```

## MinIO (object storage)

- S3‑compatible store for the raw uploaded files.
- Bucket `rag-documents` is created automatically by the `minio-setup` service in
  compose.
- API endpoint used by backend/worker: `http://localhost:9000`.

## RabbitMQ (message queue)

- Queue: `documents.ingest` — backend publishes ingest jobs, worker consumes.
- AMQP URL: `amqp://guest:guest@localhost:5672/`.
- Management UI at http://localhost:15672 to inspect queues/messages.

## Redis

- Used for ingestion job status / lightweight caching.
- URL: `redis://localhost:6379`.

## Managing the Docker stack

```bash
cd "/Users/gyanendrachouhan/Desktop/Multi Tenant RAG SaaS/tenantrag"

docker compose up -d      # start MinIO + RabbitMQ + Redis
docker compose ps         # check health
docker compose logs -f    # follow logs
docker compose down       # stop (keeps data volumes)
docker compose down -v    # stop AND delete volumes (fresh start)
```

Named volumes persist data between restarts: `minio-data`, `rabbitmq-data`,
`redis-data`.

## Secrets

Runtime secrets live in per‑service `.env` files that are **git‑ignored**:
- `rag-service/.env` → `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`, …

Commit a `.env.example` (no real values) so others know what to fill in.
