"""HTTP client for the rag-service /embed endpoint."""

from __future__ import annotations

import httpx

from app.config import settings


def embed_documents(texts: list[str]) -> list[list[float]]:
    """Call rag-service to embed a batch of passages -> list of 384-dim vectors."""
    url = f"{settings.rag_service_url}/embed"
    # Embeddings can be slow on CPU for big batches; give it a generous timeout.
    with httpx.Client(timeout=120.0) as client:
        resp = client.post(url, json={"texts": texts})
        resp.raise_for_status()
        data = resp.json()
    return data["embeddings"]
