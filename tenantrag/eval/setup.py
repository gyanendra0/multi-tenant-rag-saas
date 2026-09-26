"""
Setup: ensure the eval tenant exists and its corpus is ingested.

Idempotent — uploads only the corpus files that aren't already present (matched
by filename), then waits for each to reach READY. Run this once before the eval
scripts (they also call it, so it's safe to skip).

    python -m setup
"""

from __future__ import annotations

from pathlib import Path

from eval_common.client import BackendClient

CORPUS_DIR = Path(__file__).parent / "corpus"


def ensure_corpus(client: BackendClient) -> None:
    client.ensure_authenticated()

    existing = {d.get("filename") for d in client.list_documents()}
    corpus_files = sorted(CORPUS_DIR.glob("*.md"))
    if not corpus_files:
        raise SystemExit(f"No corpus files found in {CORPUS_DIR}")

    uploaded_ids: list[tuple[str, str]] = []
    for path in corpus_files:
        if path.name in existing:
            print(f"  = already present: {path.name}")
            continue
        doc = client.upload_document(path)
        uploaded_ids.append((doc["id"], path.name))
        print(f"  + uploaded: {path.name} -> {doc['id']} ({doc.get('status')})")

    for doc_id, name in uploaded_ids:
        status = client.wait_until_ready(doc_id)
        marker = "READY" if status == "READY" else f"!! {status}"
        print(f"  * {name}: {marker}")


def main() -> None:
    client = BackendClient()
    try:
        print("Setting up eval corpus...")
        ensure_corpus(client)
        print("Corpus ready.")
    finally:
        client.close()


if __name__ == "__main__":
    main()
