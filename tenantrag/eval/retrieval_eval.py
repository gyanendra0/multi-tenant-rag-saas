"""
Tier 1 — Retrieval evaluation (deterministic, no LLM, no cost).

For each golden question we call POST /api/rag/search and check whether the
expected source document appears among the top-K retrieved chunks. We report:

  * Hit Rate@k  — fraction of questions where the expected doc is in the top k.
  * MRR         — mean reciprocal rank of the first correct doc (0 if absent).

"Negative" questions (expected_doc = null) are excluded from these metrics; they
are only meaningful for Tier 2 (the model should decline to answer).

    python -m retrieval_eval
"""

from __future__ import annotations

import json
from pathlib import Path

from eval_common.client import BackendClient, load_golden
from eval_common.config import settings
from setup import ensure_corpus

GOLDEN = Path(__file__).parent / "datasets" / "golden.jsonl"
RESULTS = Path(__file__).parent / "results"


def first_hit_rank(chunks: list[dict], expected_doc: str, titles_by_id: dict) -> int | None:
    """1-based rank of the first chunk whose document matches expected_doc, else None.

    We match on the document title, which the API returns per chunk. The corpus
    filename (e.g. handbook.md) is used as the document title on upload, so a
    title == expected_doc comparison works.
    """
    for i, ch in enumerate(chunks, start=1):
        title = ch.get("title") or titles_by_id.get(ch.get("documentId"))
        if title == expected_doc:
            return i
    return None


def main() -> None:
    client = BackendClient()
    try:
        ensure_corpus(client)

        # Map documentId -> title as a fallback for matching.
        titles_by_id = {d["id"]: d.get("filename") or d.get("title")
                        for d in client.list_documents()}

        rows = load_golden(GOLDEN)
        graded = [r for r in rows if r.get("expected_doc")]
        k_list = settings.k_list
        max_k = max(k_list + [settings.top_k])

        hits = {k: 0 for k in k_list}
        reciprocal_ranks: list[float] = []
        per_question: list[dict] = []

        print(f"Evaluating retrieval on {len(graded)} graded questions "
              f"(top_k={max_k})...\n")

        for r in graded:
            chunks = client.search(r["question"], top_k=max_k)
            rank = first_hit_rank(chunks, r["expected_doc"], titles_by_id)
            rr = (1.0 / rank) if rank else 0.0
            reciprocal_ranks.append(rr)
            for k in k_list:
                if rank is not None and rank <= k:
                    hits[k] += 1
            per_question.append({
                "id": r["id"],
                "expected_doc": r["expected_doc"],
                "rank": rank,
                "reciprocal_rank": round(rr, 3),
            })
            status = f"rank {rank}" if rank else "MISS"
            print(f"  {r['id']:<7} {status}")

        n = len(graded)
        summary = {
            "questions": n,
            "hit_rate": {f"@{k}": round(hits[k] / n, 3) for k in k_list},
            "mrr": round(sum(reciprocal_ranks) / n, 3),
        }

        print("\n=== Retrieval summary ===")
        for k in k_list:
            print(f"  Hit Rate@{k}: {summary['hit_rate'][f'@{k}']}")
        print(f"  MRR        : {summary['mrr']}")

        RESULTS.mkdir(exist_ok=True)
        out = RESULTS / "retrieval.json"
        out.write_text(json.dumps(
            {"summary": summary, "per_question": per_question}, indent=2))
        print(f"\nWrote {out}")
    finally:
        client.close()


if __name__ == "__main__":
    main()
