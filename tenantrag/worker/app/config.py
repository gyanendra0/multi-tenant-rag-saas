"""
Worker configuration, loaded from environment variables with local-dev defaults.

Everything the worker touches (Postgres, Redis, RabbitMQ, MinIO, rag-service)
is configurable here so the same code runs locally and in Docker later.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # --- PostgreSQL (same DB as the Java backend) ---
    # We connect as the non-superuser app role so RLS applies to us too.
    pg_host: str = "localhost"
    pg_port: int = 5432
    pg_db: str = "rag_saas"
    pg_user: str = "rag_app"
    pg_password: str = "1234"

    # --- Redis (job status) ---
    redis_url: str = "redis://localhost:6379"

    # --- RabbitMQ ---
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"
    ingest_queue: str = "documents.ingest"

    # --- MinIO / S3 ---
    storage_endpoint: str = "http://localhost:9000"
    storage_region: str = "us-east-1"
    storage_access_key: str = "minioadmin"
    storage_secret_key: str = "minioadmin"
    storage_bucket: str = "rag-documents"

    # --- rag-service (embeddings) ---
    rag_service_url: str = "http://localhost:8100"

    # --- Chunking ---
    # Sliding window over tokens. 512 tokens with 64 overlap per the handoff.
    chunk_tokens: int = 512
    chunk_overlap: int = 64
    embed_batch_size: int = 64

    @property
    def pg_conninfo(self) -> str:
        return (
            f"host={self.pg_host} port={self.pg_port} dbname={self.pg_db} "
            f"user={self.pg_user} password={self.pg_password}"
        )


settings = Settings()
