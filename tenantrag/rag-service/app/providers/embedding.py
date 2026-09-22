"""
Embedding provider abstraction.

The rest of the app depends only on the `EmbeddingProvider` protocol, never on a
specific backend. That means swapping sentence-transformers for OpenAI, Ollama,
Cohere, etc. later is a one-file change plus config — no callers change.
"""

from __future__ import annotations

from typing import Protocol

from sentence_transformers import SentenceTransformer

from app.config import settings


class EmbeddingProvider(Protocol):
    """Anything that can turn texts into fixed-length vectors."""

    @property
    def dimension(self) -> int:
        """The length of each returned vector (must match the DB column)."""
        ...

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        """Embed passages/chunks to be stored and searched over."""
        ...

    def embed_query(self, text: str) -> list[float]:
        """Embed a single search query."""
        ...


class SentenceTransformersProvider:
    """
    Local, offline embeddings via the sentence-transformers library.

    The model is loaded ONCE at construction (app startup). The first ever load
    downloads the weights (~130 MB for bge-small) from Hugging Face and caches
    them in ~/.cache/huggingface; subsequent runs are offline.
    """

    def __init__(self) -> None:
        self._model = SentenceTransformer(settings.embedding_model)
        self._dimension = self._model.get_sentence_embedding_dimension()

        # Fail fast if the model's output size doesn't match what the DB expects.
        if self._dimension != settings.embedding_dim:
            raise ValueError(
                f"Model '{settings.embedding_model}' produces "
                f"{self._dimension}-dim vectors, but EMBEDDING_DIM is "
                f"{settings.embedding_dim}. Update the DB column and config so "
                f"they agree."
            )

    @property
    def dimension(self) -> int:
        return self._dimension

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        # normalize_embeddings=True => unit-length vectors, which makes cosine
        # similarity (what our HNSW index uses) behave well.
        vectors = self._model.encode(
            texts,
            batch_size=settings.embedding_batch_size,
            normalize_embeddings=True,
            convert_to_numpy=True,
        )
        return vectors.tolist()

    def embed_query(self, text: str) -> list[float]:
        # bge models want a special instruction prefix on QUERIES only.
        prefixed = settings.bge_query_prefix + text
        vector = self._model.encode(
            prefixed,
            normalize_embeddings=True,
            convert_to_numpy=True,
        )
        return vector.tolist()
