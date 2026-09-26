# RAG Evaluation

Measures RAG quality **before** building the UI, in two tiers:

| Tier | Script | Measures | Cost |
|------|--------|----------|------|
| **1 — Retrieval** | `retrieval_eval.py` | Hit Rate@k, MRR | Free, deterministic |
| **2 — Generation** | `generation_eval.py` | Faithfulness, Answer Relevancy, Context Precision/Recall (RAGAS, Groq judge) | LLM API calls |

Everything runs against the **live backend** (`/api/rag/search`, `/api/chat`) as
an authenticated, RLS-scoped eval tenant — so we measure the real system.

## What the metrics mean

- **Hit Rate@k** — for what fraction of questions is the correct source document
  among the top *k* retrieved chunks? The single most useful retrieval number.
- **MRR** (Mean Reciprocal Rank) — rewards ranking the correct doc higher (1/rank).
- **Faithfulness** — is every claim in the answer supported by the retrieved
  context? Low faithfulness = hallucination. The most important generation metric.
- **Answer Relevancy** — does the answer address the question (not waffle)?
- **Context Precision / Recall** — quality of the retrieved context relative to
  the reference answer.

## Layout

```
eval/
  corpus/            # small test documents (source of truth)
  datasets/
    golden.jsonl     # {question, ground_truth, expected_doc}
  eval_common/       # shared API client + config
  setup.py           # upload + ingest corpus into the eval tenant (idempotent)
  retrieval_eval.py  # Tier 1
  generation_eval.py # Tier 2
  results/           # JSON/CSV outputs (git-ignored)
```

## Prerequisites

The full stack must be running (see `../docs/getting-started.md`): Postgres,
docker infra, rag-service (:8100), worker, and backend (:8080).

## Run it

```bash
cd tenantrag/eval
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Tier 2 needs a judge key (same Groq key works):
cp .env.example .env    # then set LLM_API_KEY

# 1) Prepare the eval tenant + corpus (idempotent; the eval scripts also call it)
python -m setup

# 2) Tier 1 — retrieval (fast, free)
python -m retrieval_eval

# 3) Tier 2 — generation quality (calls the Groq judge)
python -m generation_eval
```

Results are written to `results/` and printed as a summary.

## Notes

- The corpus filename is used as the document **title**, so retrieval matching
  compares the chunk's title to `expected_doc` in the golden set.
- **Negative questions** (`expected_doc: null`) test that the model declines to
  answer when the context lacks the info — they're scored only in Tier 2.
- The eval tenant is isolated from real data; re-runs reuse the same account and
  skip already-uploaded corpus files.
