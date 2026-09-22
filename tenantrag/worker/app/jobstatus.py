"""
Publish ingestion job status to Redis so the UI can poll progress.

Key: job:{documentId} -> hash {status, progress, error, updated_at}, TTL 1h.
This mirrors the handoff's job-status contract.
"""

from __future__ import annotations

import time

import redis

from app.config import settings

_r = redis.Redis.from_url(settings.redis_url, decode_responses=True)

_TTL_SECONDS = 3600


def set_job(document_id: str, status: str, progress: int,
            error: str | None = None) -> None:
    key = f"job:{document_id}"
    _r.hset(key, mapping={
        "status": status,
        "progress": str(progress),
        "error": error or "",
        "updated_at": str(int(time.time())),
    })
    _r.expire(key, _TTL_SECONDS)
