"""
RAG service — FastAPI app (Phase 7, step 2).

For now it exposes just embeddings:
  GET  /health          -> liveness + model info
  POST /embed           -> embed a batch of document passages
  POST /embed/query     -> embed a single search query (bge query prefix applied)

This service is INTERNAL only (never exposed publicly). The Java worker/backend
call it over the internal network. No auth here by design.

Later phases add POST /query (retrieval + LLM answer).
"""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.config import settings
from app.providers.embedding import SentenceTransformersProvider
from app.providers.llm import OpenAICompatibleProvider

# The provider is created at startup and stored on app.state so the (heavy)
# model is loaded exactly once for the whole process.
_provider: SentenceTransformersProvider | None = None
# The LLM client is cheap to build (no local weights), created lazily on first use.
_llm: OpenAICompatibleProvider | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _provider
    # Loading the model may download weights on first run — this is why we do it
    # at startup, not per request.
    _provider = SentenceTransformersProvider()
    yield
    _provider = None


app = FastAPI(title="TenantRAG RAG Service", lifespan=lifespan)


# ---- Schemas ---------------------------------------------------------------

class EmbedRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1, description="Passages to embed")


class EmbedResponse(BaseModel):
    model: str
    dimension: int
    embeddings: list[list[float]]


class EmbedQueryRequest(BaseModel):
    text: str = Field(..., min_length=1, description="A single search query")


class EmbedQueryResponse(BaseModel):
    model: str
    dimension: int
    embedding: list[float]


class GenerateRequest(BaseModel):
    system: str = Field(..., min_length=1, description="System prompt / instructions")
    prompt: str = Field(..., min_length=1, description="User prompt (question + context)")


class GenerateResponse(BaseModel):
    model: str
    answer: str


# ---- Routes ----------------------------------------------------------------

@app.get("/health")
def health() -> dict:
    ready = _provider is not None
    return {
        "status": "ok" if ready else "loading",
        "model": settings.embedding_model,
        "dimension": _provider.dimension if ready else settings.embedding_dim,
    }


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest) -> EmbedResponse:
    assert _provider is not None
    vectors = _provider.embed_documents(req.texts)
    return EmbedResponse(
        model=settings.embedding_model,
        dimension=_provider.dimension,
        embeddings=vectors,
    )


@app.post("/embed/query", response_model=EmbedQueryResponse)
def embed_query(req: EmbedQueryRequest) -> EmbedQueryResponse:
    assert _provider is not None
    vector = _provider.embed_query(req.text)
    return EmbedQueryResponse(
        model=settings.embedding_model,
        dimension=_provider.dimension,
        embedding=vector,
    )


@app.post("/generate", response_model=GenerateResponse)
def generate(req: GenerateRequest) -> GenerateResponse:
    """Phase 8 — produce a grounded answer from a system + user prompt.

    The backend builds the prompt (question + retrieved context) and calls this;
    keeping the LLM here mirrors how embeddings are centralized in this service.
    """
    global _llm
    if _llm is None:
        _llm = OpenAICompatibleProvider()
    answer, model_used = _llm.generate(req.system, req.prompt)
    return GenerateResponse(model=model_used, answer=answer)
