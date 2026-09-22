# RAG Service

Internal FastAPI service for the Multi-Tenant RAG SaaS. Phase 7 (step 2) exposes
local, offline **embeddings** via `sentence-transformers`
(`BAAI/bge-small-en-v1.5`, 384-dim). Later phases add retrieval + LLM answering.

> Internal only — never exposed publicly. The Java backend/worker call it on the
> internal network. No auth here by design.

## Run locally (native venv)

```bash
cd tenantrag/rag-service

# 1) Create + activate a virtualenv
python3 -m venv .venv
source .venv/bin/activate

# 2) Install deps (first time pulls torch + downloads the model on first run)
pip install -r requirements.txt

# 3) Start the service on :8100
uvicorn app.main:app --host 0.0.0.0 --port 8100 --reload
```

The **first** startup downloads the model (~130 MB) from Hugging Face and caches
it in `~/.cache/huggingface`. After that it runs fully offline.

## Test

```bash
# Health (shows model + dimension)
curl -s http://localhost:8100/health | jq

# Embed two passages -> two 384-length vectors
curl -s -X POST http://localhost:8100/embed \
  -H "Content-Type: application/json" \
  -d '{"texts":["hello world","the cat sat on the mat"]}' \
  | jq '{model, dimension, count: (.embeddings|length), first_len: (.embeddings[0]|length)}'

# Embed a query (bge query prefix applied internally)
curl -s -X POST http://localhost:8100/embed/query \
  -H "Content-Type: application/json" \
  -d '{"text":"where did the cat sit?"}' \
  | jq '{model, dimension, len: (.embedding|length)}'
```

Expected: `dimension: 384`, and each vector has length 384.

## Interactive docs

FastAPI auto-generates Swagger UI at http://localhost:8100/docs

## Config (env vars, all optional)

| Var | Default | Meaning |
|-----|---------|---------|
| `EMBEDDING_MODEL` | `BAAI/bge-small-en-v1.5` | sentence-transformers model |
| `EMBEDDING_DIM` | `384` | must match `document_chunk.embedding` |
| `EMBEDDING_BATCH_SIZE` | `64` | texts per encode batch |

## LLM (Phase 8) — `POST /generate`

The service also answers with a configured **OpenAI-compatible** LLM. Set these
env vars (or a `.env` file in `tenantrag/rag-service/`). Only the API key is
required for hosted providers.

| Var | Default | Meaning |
|-----|---------|---------|
| `LLM_BASE_URL` | `https://api.groq.com/openai/v1` | provider endpoint |
| `LLM_MODEL` | `openai/gpt-oss-120b` | primary model (tried first) |
| `LLM_FALLBACK_MODELS` | `llama-3.3-70b-versatile` | comma-separated fallbacks, in order |
| `LLM_API_KEY` | _(empty)_ | provider key (blank ok for local Ollama) |
| `LLM_TEMPERATURE` | `0.2` | lower = more grounded |
| `LLM_MAX_TOKENS` | `1024` | answer length cap |
| `LLM_TIMEOUT_SECONDS` | `60` | per-request timeout |

**Model fallback:** the primary (`LLM_MODEL`) is tried first; if it fails (rate
limit, model unavailable, transient error) each model in `LLM_FALLBACK_MODELS` is
tried in order. The first to answer wins, and `/generate` reports which model
actually produced the answer.

Provider examples (best open-source models via API):

```bash
# Groq (free tier, very fast) — gpt-oss-120b primary, Llama 3.3 70B fallback
export LLM_BASE_URL=https://api.groq.com/openai/v1
export LLM_MODEL=openai/gpt-oss-120b
export LLM_FALLBACK_MODELS=llama-3.3-70b-versatile
export LLM_API_KEY=gsk_...          # from https://console.groq.com

# Together AI
# export LLM_BASE_URL=https://api.together.xyz/v1
# export LLM_MODEL=meta-llama/Llama-3.3-70B-Instruct-Turbo
# export LLM_API_KEY=...

# OpenRouter
# export LLM_BASE_URL=https://openrouter.ai/api/v1
# export LLM_MODEL=meta-llama/llama-3.3-70b-instruct
# export LLM_API_KEY=...

# Local Ollama (no key)
# export LLM_BASE_URL=http://localhost:11434/v1
# export LLM_MODEL=llama3.2
```

Or create `tenantrag/rag-service/.env` (loaded automatically):

```dotenv
LLM_BASE_URL=https://api.groq.com/openai/v1
LLM_MODEL=openai/gpt-oss-120b
LLM_FALLBACK_MODELS=llama-3.3-70b-versatile
LLM_API_KEY=gsk_your_key_here
LLM_TEMPERATURE=0.2
LLM_MAX_TOKENS=1024
```


Test the endpoint directly:
```bash
curl -s -X POST http://localhost:8100/generate \
  -H "Content-Type: application/json" \
  -d '{"system":"You are terse.","prompt":"Say hello in 3 words."}' | jq
```

| `PORT` | `8100` | informational |
