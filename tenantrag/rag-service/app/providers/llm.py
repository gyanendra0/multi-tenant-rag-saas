"""
LLM provider abstraction (Phase 8).

Like the embedding provider, callers depend only on the `LLMProvider` protocol,
so swapping providers (Groq / Together / OpenRouter / Fireworks / DeepSeek /
local Ollama) is a config change, not a code change. All of these speak the
OpenAI-compatible Chat Completions API, so one client covers them all.

Supports an ordered list of models: the primary is tried first, then each
fallback in turn if a call fails (rate limit, model unavailable, transient
error). The first model to answer wins.
"""

from __future__ import annotations

import logging
from typing import Protocol

from openai import OpenAI

from app.config import settings

log = logging.getLogger("rag.llm")


class LLMProvider(Protocol):
    """Anything that can turn a system+user prompt into an answer string."""

    def generate(self, system_prompt: str, user_prompt: str) -> tuple[str, str]:
        """Return (answer, model_used)."""
        ...


class OpenAICompatibleProvider:
    """
    Chat completions via any OpenAI-compatible endpoint, with model fallback.

    Configured by `llm_base_url`, `llm_models` (primary + fallbacks), and
    `llm_api_key`. Hosted providers require the API key; a local Ollama does not.
    """

    def __init__(self) -> None:
        # A blank key is allowed for keyless local servers (e.g. Ollama), where
        # the OpenAI client still needs *some* string.
        self._client = OpenAI(
            base_url=settings.llm_base_url,
            api_key=settings.llm_api_key or "not-needed",
            timeout=settings.llm_timeout_seconds,
        )
        self._models = settings.llm_models

    @property
    def models(self) -> list[str]:
        return self._models

    def generate(self, system_prompt: str, user_prompt: str) -> tuple[str, str]:
        messages = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ]

        last_error: Exception | None = None
        for model in self._models:
            try:
                resp = self._client.chat.completions.create(
                    model=model,
                    messages=messages,
                    temperature=settings.llm_temperature,
                    max_tokens=settings.llm_max_tokens,
                )
                answer = (resp.choices[0].message.content or "").strip()
                return answer, model
            except Exception as exc:  # noqa: BLE001 - try the next model
                last_error = exc
                log.warning("LLM model '%s' failed (%s); trying next fallback",
                            model, exc)

        # All models failed — surface the last error to the caller.
        raise RuntimeError(
            f"All LLM models failed {self._models}: {last_error}"
        ) from last_error

