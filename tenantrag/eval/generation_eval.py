"""
Tier 2 — Generation evaluation with RAGAS (LLM-as-judge via Groq).

For each golden question we call POST /api/chat, then also fetch the retrieved
context via POST /api/rag/search (chat doesn't return raw context). We score with
RAGAS:

  * faithfulness       — is the answer supported by the retrieved context?
                         (the key anti-hallucination metric)
  * answer_relevancy   — does the answer actually address the question?
  * context_precision  — are the retrieved chunks relevant (ranked well)?
  * context_recall     — did retrieval bring back what the reference answer needs?

The judge is a Groq model (OpenAI-compatible); embeddings for the metrics that
need them run locally (bge-small) so there's no paid embeddings dependency.

Requires LLM_API_KEY. Negative questions are included so faithfulness rewards the
model for declining when the answer isn't in context.

    python -m generation_eval
"""

from __future__ import annotations

import json
from pathlib import Path

from eval_common.client import BackendClient, load_golden
from eval_common.config import settings
from setup import ensure_corpus

GOLDEN = Path(__file__).parent / "datasets" / "golden.jsonl"
RESULTS = Path(__file__).parent / "results"


def preflight_judge_model() -> None:
    """Verify the judge model is reachable before firing 100 scoring calls.

    Fails fast with the list of accessible models instead of a wall of 404s.
    """
    import httpx

    headers = {"Authorization": f"Bearer {settings.llm_api_key}"}
    try:
        r = httpx.get(f"{settings.llm_base_url.rstrip('/')}/models",
                      headers=headers, timeout=30.0)
        r.raise_for_status()
        ids = sorted(m["id"] for m in r.json().get("data", []))
    except Exception as exc:  # network / auth problems
        raise SystemExit(f"Could not list judge models: {exc}")

    if settings.judge_model not in ids:
        listed = "\n  ".join(ids) or "(none returned)"
        raise SystemExit(
            f"Judge model '{settings.judge_model}' is not available to this key.\n"
            f"Set JUDGE_MODEL in eval/.env to one of:\n  {listed}")
    print(f"Judge model OK: {settings.judge_model}")


def build_judge():
    """Construct the RAGAS LLM + embeddings wrappers pointed at Groq/local."""
    if not settings.llm_api_key:
        raise SystemExit(
            "LLM_API_KEY is required for Tier 2. Set it in eval/.env or the env.")

    from langchain_openai import ChatOpenAI
    from langchain_huggingface import HuggingFaceEmbeddings
    from ragas.llms import LangchainLLMWrapper
    from ragas.embeddings import LangchainEmbeddingsWrapper

    judge_llm = ChatOpenAI(
        model=settings.judge_model,
        base_url=settings.llm_base_url,
        api_key=settings.llm_api_key,
        temperature=0.0,
        timeout=settings.judge_timeout_seconds,
        max_retries=settings.judge_max_retries,
    )
    judge_emb = HuggingFaceEmbeddings(model_name=settings.judge_embedding_model)
    return (LangchainLLMWrapper(judge_llm),
            LangchainEmbeddingsWrapper(judge_emb))


def main() -> None:
    client = BackendClient()
    try:
        ensure_corpus(client)

        rows = load_golden(GOLDEN)
        print(f"Collecting answers + contexts for {len(rows)} questions...\n")

        # Cache answers/contexts so a crash (e.g. rate limit) doesn't force us to
        # re-call the chat model from scratch — we resume from what we have.
        RESULTS.mkdir(exist_ok=True)
        cache_path = RESULTS / "answers_cache.json"
        cache: dict[str, dict] = {}
        if cache_path.exists():
            cache = json.loads(cache_path.read_text())
            print(f"  (resuming: {len(cache)} cached answers)")

        samples = []
        for r in rows:
            qid = r["id"]
            if qid in cache:
                sample = cache[qid]
                print(f"  {qid:<7} cached")
            else:
                chat = client.chat(r["question"])
                chunks = client.search(r["question"], top_k=settings.top_k)
                contexts = [c.get("content", "") for c in chunks]
                sample = {
                    "user_input": r["question"],
                    "response": chat.get("answer", ""),
                    "retrieved_contexts": contexts,
                    "reference": r.get("ground_truth", ""),
                }
                cache[qid] = sample
                cache_path.write_text(json.dumps(cache, indent=2))
                print(f"  {qid:<7} answered ({len(contexts)} contexts)")
            samples.append(sample)

        print("\nScoring with RAGAS (this calls the Groq judge)...")
        preflight_judge_model()
        from datasets import Dataset
        from ragas import evaluate
        from ragas.run_config import RunConfig
        from ragas.metrics import (
            faithfulness,
            answer_relevancy,
            context_precision,
            context_recall,
        )

        judge_llm, judge_emb = build_judge()
        dataset = Dataset.from_list(samples)
        run_config = RunConfig(
            max_workers=settings.judge_max_workers,
            timeout=settings.judge_timeout_seconds,
            max_retries=settings.judge_max_retries,
            # Wait up to a full TPM window between retries on 429s.
            max_wait=90,
        )
        result = evaluate(
            dataset,
            metrics=[faithfulness, answer_relevancy,
                     context_precision, context_recall],
            llm=judge_llm,
            embeddings=judge_emb,
            run_config=run_config,
        )

        df = result.to_pandas()
        metric_cols = [c for c in df.columns
                       if c in ("faithfulness", "answer_relevancy",
                                "context_precision", "context_recall")]
        summary = {c: round(float(df[c].mean(skipna=True)), 3) for c in metric_cols}

        print("\n=== Generation summary (mean) ===")
        for c in metric_cols:
            print(f"  {c:<18}: {summary[c]}")

        RESULTS.mkdir(exist_ok=True)
        (RESULTS / "generation.json").write_text(json.dumps(summary, indent=2))
        df.to_csv(RESULTS / "generation_per_question.csv", index=False)
        print(f"\nWrote {RESULTS / 'generation.json'} and "
              f"{RESULTS / 'generation_per_question.csv'}")
    finally:
        client.close()


if __name__ == "__main__":
    main()
