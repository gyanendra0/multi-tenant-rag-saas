"""
Shared configuration for the eval scripts, loaded from environment variables
(and an optional local .env). Safe local-dev defaults so it runs with zero
config against a stack started via docs/getting-started.md.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class EvalSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # --- Backend under test ---
    api_url: str = "http://localhost:8080"

    # --- Dedicated eval tenant (kept separate from real data) ---
    # A unique-ish slug avoids clashing with an existing tenant on re-runs; the
    # setup step reuses the account if it already exists.
    eval_email: str = "eval@ragbench.local"
    eval_password: str = "EvalPassw0rd!"
    eval_full_name: str = "RAG Eval"
    eval_tenant_name: str = "RAG Eval Bench"
    eval_tenant_slug: str = "rag-eval-bench"

    # --- Retrieval settings ---
    top_k: int = 5
    # Ranks to report Hit Rate / Recall at.
    k_values: str = "1,3,5"

    # --- Ingestion polling ---
    ingest_timeout_seconds: int = 180
    poll_interval_seconds: float = 2.0

    # --- LLM judge (Tier 2, RAGAS via Groq OpenAI-compatible API) ---
    llm_base_url: str = "https://api.groq.com/openai/v1"
    llm_api_key: str = ""  # REQUIRED for Tier 2; set LLM_API_KEY
    # Judge model — must be one your Groq account can access. Override via
    # JUDGE_MODEL in eval/.env. gpt-oss-120b is what the app itself uses and is
    # known-good here; llama-3.3-70b-versatile may 404 on some accounts.
    judge_model: str = "openai/gpt-oss-120b"
    # Local embeddings for RAGAS metrics that need them (no paid API).
    judge_embedding_model: str = "BAAI/bge-small-en-v1.5"

    # --- RAGAS run tuning (avoid rate-limit/timeout storms) ---
    # Groq free tier is easily overwhelmed by RAGAS' default 16 parallel workers.
    # Keep concurrency low and timeouts generous; retry transient failures.
    judge_max_workers: int = 1
    judge_timeout_seconds: int = 300
    judge_max_retries: int = 10

    @property
    def k_list(self) -> list[int]:
        return [int(k.strip()) for k in self.k_values.split(",") if k.strip()]


settings = EvalSettings()
