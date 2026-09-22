# Getting Started (beginner‑friendly)

This guide gets the whole project running locally on **macOS**. Follow it top to
bottom. Copy/paste each command into the Terminal.

> Notation: `$` means "type this in a normal Terminal". Lines starting with `#`
> are comments — you don't type those.

---

## 0. What you need installed (prerequisites)

| Tool | Why | Install |
|------|-----|---------|
| **Java 21** | Runs the backend | `brew install openjdk@21` |
| **Maven** | Builds the backend (or use the included `./mvnw`) | bundled wrapper |
| **Python 3.12** | Runs rag-service + worker | `brew install python@3.12` |
| **Docker Desktop** | Runs MinIO/RabbitMQ/Redis | https://www.docker.com |
| **PostgreSQL 16 + pgvector** | Database | `brew install postgresql@16 pgvector` |

Start Postgres once installed:

```bash
brew services start postgresql@16
```

---

## 1. Start the infrastructure (Docker)

From the `tenantrag/` folder:

```bash
cd "/Users/gyanendrachouhan/Desktop/Multi Tenant RAG SaaS/tenantrag"
docker compose up -d
```

This starts **MinIO** (file storage), **RabbitMQ** (job queue) and **Redis**.
Check they are healthy:

```bash
docker compose ps
```

- MinIO console → http://localhost:9001  (login `minioadmin` / `minioadmin`)
- RabbitMQ console → http://localhost:15672  (login `guest` / `guest`)

---

## 2. Create the database

```bash
# create the database and the app role used by the backend
createdb rag_saas
psql rag_saas -c "CREATE ROLE rag_app LOGIN PASSWORD '1234';"
psql rag_saas -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

The backend runs Flyway migrations automatically on startup, which create the
tables, RLS policies and indexes. You do **not** create tables by hand.

---

## 3. Start the rag-service (embeddings + LLM)

```bash
cd "/Users/gyanendrachouhan/Desktop/Multi Tenant RAG SaaS/tenantrag/rag-service"

python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Create the secrets file `rag-service/.env` (this file is **git‑ignored** — never
commit it). Copy from the example and add your Groq key:

```bash
cp .env.example .env   # then edit .env
```

`.env` should contain:

```dotenv
LLM_BASE_URL=https://api.groq.com/openai/v1
LLM_API_KEY=gsk_your_groq_key_here
LLM_MODEL=openai/gpt-oss-120b
LLM_FALLBACK_MODELS=llama-3.3-70b-versatile
```

Run it:

```bash
uvicorn app.main:app --port 8100
```

The first run downloads the embedding model (~130 MB) — that's normal.

---

## 4. Start the worker (document ingestion)

Open a **new** Terminal tab:

```bash
cd "/Users/gyanendrachouhan/Desktop/Multi Tenant RAG SaaS/tenantrag/worker"

python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m app.main
```

The worker connects to RabbitMQ and waits for ingest jobs.

---

## 5. Start the backend (the main API)

Open another Terminal tab:

```bash
cd "/Users/gyanendrachouhan/Desktop/Multi Tenant RAG SaaS/tenantrag/backend"
./mvnw spring-boot:run
```

The API is now at **http://localhost:8080**.

---

## 6. Try it end‑to‑end

```bash
# 1) Register (creates a tenant + first user)
curl -s http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@acme.com","password":"Passw0rd!","fullName":"Me",
       "tenantName":"Acme","tenantSlug":"acme"}'

# 2) Login → copy the accessToken from the response
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"me@acme.com","password":"Passw0rd!"}'

TOKEN=paste_access_token_here

# 3) Upload a document
curl -s http://localhost:8080/api/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F 'file=@/path/to/some.pdf'

# 4) Wait a few seconds for ingestion, then chat
curl -s http://localhost:8080/api/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is this document about?"}'
```

You should get a grounded answer with `citations`.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| Backend can't connect to DB | Postgres not started → `brew services start postgresql@16` |
| Chat returns 422 from rag-service | Old bug — already fixed via `SimpleClientHttpRequestFactory`. Rebuild backend with `./mvnw clean spring-boot:run` |
| `LLM_API_KEY` errors | `.env` missing or key invalid |
| Upload stays `PENDING` | Worker not running, or RabbitMQ down (`docker compose ps`) |
| Model re‑downloads every run | Normal only on first run; cache lives in the venv |
