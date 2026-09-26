"""
A thin client over the backend REST API used by both eval tiers.

It logs in (registering the eval tenant on first run), lists/uploads documents,
and calls the retrieval + chat endpoints. Everything runs through the *real*
authenticated, RLS-scoped API path — so we evaluate the system as users see it.
"""

from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any, Optional

import httpx

from .config import settings


class BackendClient:
    def __init__(self, base_url: Optional[str] = None) -> None:
        self.base_url = (base_url or settings.api_url).rstrip("/")
        self._client = httpx.Client(base_url=self.base_url, timeout=60.0)
        self._token: Optional[str] = None

    # -- auth ---------------------------------------------------------------
    def register_if_needed(self) -> None:
        """Register the eval tenant. Ignores 409 (already exists)."""
        payload = {
            "email": settings.eval_email,
            "password": settings.eval_password,
            "fullName": settings.eval_full_name,
            "tenantName": settings.eval_tenant_name,
            "tenantSlug": settings.eval_tenant_slug,
        }
        r = self._client.post("/api/auth/register", json=payload)
        if r.status_code in (200, 201):
            return
        if r.status_code == 409:
            return  # account already exists — fine
        r.raise_for_status()

    def login(self) -> None:
        r = self._client.post(
            "/api/auth/login",
            json={"email": settings.eval_email, "password": settings.eval_password},
        )
        r.raise_for_status()
        self._token = r.json()["accessToken"]
        self._client.headers["Authorization"] = f"Bearer {self._token}"

    def ensure_authenticated(self) -> None:
        self.register_if_needed()
        self.login()

    # -- documents ----------------------------------------------------------
    def list_documents(self) -> list[dict[str, Any]]:
        r = self._client.get("/api/documents", params={"page": 0, "size": 200})
        r.raise_for_status()
        body = r.json()
        # Spring Page returns {content: [...]}; be tolerant of a plain list too.
        return body.get("content", body) if isinstance(body, dict) else body

    def upload_document(self, path: Path) -> dict[str, Any]:
        with path.open("rb") as fh:
            files = {"file": (path.name, fh, "text/markdown")}
            r = self._client.post("/api/documents", files=files)
        r.raise_for_status()
        return r.json()

    def wait_until_ready(self, document_id: str) -> str:
        """Poll a document until it leaves PENDING/PROCESSING. Returns status."""
        deadline = time.time() + settings.ingest_timeout_seconds
        while time.time() < deadline:
            r = self._client.get(f"/api/documents/{document_id}")
            r.raise_for_status()
            status = r.json().get("status")
            if status in ("READY", "FAILED"):
                return status
            time.sleep(settings.poll_interval_seconds)
        raise TimeoutError(f"Document {document_id} not ready in time")

    # -- rag ----------------------------------------------------------------
    def search(self, question: str, top_k: int) -> list[dict[str, Any]]:
        r = self._client.post(
            "/api/rag/search",
            params={"topK": top_k},
            json={"question": question},
        )
        r.raise_for_status()
        return r.json()

    def chat(self, question: str) -> dict[str, Any]:
        r = self._client.post("/api/chat", json={"question": question})
        r.raise_for_status()
        return r.json()

    def close(self) -> None:
        self._client.close()


def load_golden(path: Path) -> list[dict[str, Any]]:
    """Read a JSONL golden dataset into a list of dicts."""
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows
