# Ingestion Worker

Python worker (Phase 7, step 3). Consumes `documents.ingest` messages from
RabbitMQ and runs the pipeline:

```
download (MinIO) -> parse -> clean + chunk (512 tok / 64 overlap)
   -> embed (rag-service /embed) -> upsert document_chunk (RLS) -> status READY
```

Also writes progress to Redis (`job:{documentId}`).

## Prerequisites (all already running)

- Docker services up: `docker compose up -d` (RabbitMQ, Redis, MinIO)
- Postgres with V4 applied (`document_chunk.embedding = halfvec(384)`)
- **rag-service running** on :8100 (embeddings)

## Run locally (native venv)

```bash
cd tenantrag/worker

python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Start consuming
python -m app.main
```

You should see: `Waiting for messages on 'documents.ingest'.`

## Test end-to-end WITHOUT the Java backend

The Java upload endpoint doesn't publish to the queue yet (that's step 4). To
test the worker now, upload a file so it exists in MinIO + DB, then publish a
fake message with its ids.

1. Upload a document via the API (returns the document `id`, and the DB row has
   `tenant_id`, `storage_key`). Or seed one manually.
2. Publish a message (in a second terminal, venv active):

```bash
python -m app.publish_test <documentId> <tenantId> <storageKey> text/plain <filename>
```

3. Watch the worker terminal: it should log `Ingest start ... Ingest done`.
4. Verify chunks landed (as the app role so RLS applies):

```bash
psql -d rag_saas -U rag_app -c \
  "SELECT set_config('app.current_tenant','<tenantId>',true);
   SELECT count(*) FROM document_chunk WHERE document_id='<documentId>';"

# And the document flipped to READY:
psql -d rag_saas -U rag_app -c \
  "SELECT set_config('app.current_tenant','<tenantId>',true);
   SELECT status, chunk_count FROM document WHERE id='<documentId>';"
```

## Config (env vars, optional — see app/config.py for all)

| Var | Default |
|-----|---------|
| `PG_HOST`/`PG_PORT`/`PG_DB`/`PG_USER`/`PG_PASSWORD` | localhost/5432/rag_saas/rag_app/1234 |
| `RABBITMQ_URL` | amqp://guest:guest@localhost:5672/ |
| `REDIS_URL` | redis://localhost:6379 |
| `STORAGE_ENDPOINT` | http://localhost:9000 |
| `STORAGE_BUCKET` | rag-documents |
| `RAG_SERVICE_URL` | http://localhost:8100 |
| `CHUNK_TOKENS`/`CHUNK_OVERLAP` | 512 / 64 |
