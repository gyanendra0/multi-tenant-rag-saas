"""
Clean text and split it into overlapping, token-sized chunks.

Chunking strategy (per handoff): sliding window of `chunk_tokens` tokens with
`chunk_overlap` overlap, counting tokens with tiktoken (cl100k_base). Overlap
preserves context across chunk boundaries so retrieval doesn't miss answers that
straddle a split.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

import tiktoken

from app.config import settings

# cl100k_base is the tokenizer used by modern OpenAI models; it's a fine,
# model-agnostic way to count tokens for chunk sizing.
_enc = tiktoken.get_encoding("cl100k_base")


@dataclass
class Chunk:
    index: int
    content: str
    token_count: int


def _clean(text: str) -> str:
    # Normalize whitespace: collapse runs of spaces/tabs, trim, and collapse
    # 3+ newlines down to a paragraph break.
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def chunk_text(text: str) -> list[Chunk]:
    """Split cleaned text into overlapping token windows."""
    cleaned = _clean(text)
    if not cleaned:
        return []

    tokens = _enc.encode(cleaned)
    window = settings.chunk_tokens
    overlap = settings.chunk_overlap
    step = max(window - overlap, 1)

    chunks: list[Chunk] = []
    idx = 0
    for start in range(0, len(tokens), step):
        piece = tokens[start:start + window]
        if not piece:
            break
        content = _enc.decode(piece).strip()
        if content:
            chunks.append(Chunk(index=idx, content=content, token_count=len(piece)))
            idx += 1
        if start + window >= len(tokens):
            break
    return chunks
