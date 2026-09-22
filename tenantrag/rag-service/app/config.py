"""
Configuration for the RAG service, loaded from environment variables.

All settings have safe local-dev defaults so the service runs with zero config.
Override any of them with an env var of the same name (upper-cased), e.g.
    EMBEDDING_MODEL=BAAI/bge-base-en-v1.5 uvicorn app.main:app
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # The sentence-transformers model to load. Default is our 384-dim choice.
    embedding_model: str = "BAAI/bge-small-en-v1.5"

    # Vector dimension the model produces. MUST match the DB column
    # document_chunk.embedding (halfvec(384)). Used only for a startup sanity
    # check and to report via /health.
    embedding_dim: int = 384

    # How many texts to encode per batch. Larger = faster but more memory.
    embedding_batch_size: int = 64

    # bge models are trained so that *search queries* get a special instruction
    # prefix, while *documents* are embedded as-is. Encoding both sides the same
    # way hurts retrieval quality, so we prefix queries with this string.
    # See: https://huggingface.co/BAAI/bge-small-en-v1.5#usage
    bge_query_prefix: str = (
        "Represent this sentence for searching relevant passages: "
    )

    # Port the service listens on (informational; uvicorn --port controls it).
    port: int = 8100

    # ---- LLM (Phase 8) --------------------------------------------------
    # Any OpenAI-compatible chat API. Default targets Groq (free, fast, serves
    # open models like gpt-oss and Llama 3.3 70B). Swap base_url + models for
    # Together, OpenRouter, Fireworks, DeepSeek, a local Ollama, etc.
    #   Groq:       https://api.groq.com/openai/v1
    #   Together:   https://api.together.xyz/v1
    #   Ollama:     http://localhost:11434/v1
    llm_base_url: str = "https://api.groq.com/openai/v1"
    # Primary model tried first.
    llm_model: str = "openai/gpt-oss-120b"
    # Comma-separated fallback models tried in order if the primary fails
    # (rate limit, model unavailable, transient error). First success wins.
    llm_fallback_models: str = "llama-3.3-70b-versatile"
    # API key for the provider. REQUIRED for hosted providers (set LLM_API_KEY).
    # Leave blank for a keyless local Ollama.
    llm_api_key: str = ""
    # Sampling + limits. Low temperature keeps answers grounded/factual.
    llm_temperature: float = 0.2
    llm_max_tokens: int = 1024
    # HTTP timeout (seconds) for a completion.
    llm_timeout_seconds: float = 60.0

    @property
    def llm_models(self) -> list[str]:
        """Primary model followed by any configured fallbacks, in order."""
        models = [self.llm_model.strip()]
        for m in self.llm_fallback_models.split(","):
            m = m.strip()
            if m and m not in models:
                models.append(m)
        return models


settings = Settings()
