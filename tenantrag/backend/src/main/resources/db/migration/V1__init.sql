
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS vector;

-- Tenants
CREATE TABLE tenant (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name          TEXT NOT NULL,
  slug          TEXT NOT NULL UNIQUE,
  plan          TEXT NOT NULL DEFAULT 'free',   -- free | pro | enterprise
  status        TEXT NOT NULL DEFAULT 'active', -- active | suspended | archived
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Users (global identity; membership links to tenants)
CREATE TABLE "user" (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email         CITEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  full_name     TEXT NOT NULL,
  is_active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- (require citext extension, or use TEXT + UNIQUE lower(email) index)

CREATE TABLE tenant_member (
  tenant_id   UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
  role        TEXT NOT NULL CHECK (role IN ('ORG_OWNER','ADMIN','EDITOR','VIEWER')),
  invited_by  UUID REFERENCES "user"(id),
  joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, user_id)
);

-- Refresh tokens (hashed, rotating)
CREATE TABLE refresh_token (
  id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id      UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
  tenant_id    UUID REFERENCES tenant(id) ON DELETE SET NULL,
  token_hash   TEXT NOT NULL UNIQUE,
  expires_at   TIMESTAMPTZ NOT NULL,
  revoked_at   TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Documents
CREATE TABLE document (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  tenant_id     UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  uploaded_by   UUID NOT NULL REFERENCES "user"(id),
  title         TEXT NOT NULL,
  filename      TEXT NOT NULL,
  mime_type     TEXT NOT NULL,
  size_bytes    BIGINT NOT NULL,
  storage_key   TEXT NOT NULL,             -- minio key
  status        TEXT NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','PROCESSING','READY','FAILED')),
  error_message TEXT,
  chunk_count   INT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_tenant ON document(tenant_id, created_at DESC);

-- Chunks + embeddings (this IS the vector table)
CREATE TABLE document_chunk (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  tenant_id     UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  document_id   UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  chunk_index   INT NOT NULL,
  content       TEXT NOT NULL,
  token_count   INT NOT NULL,
  embedding     halfvec(1536) NOT NULL,    -- text-embedding-3-small dim
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_chunk_doc ON document_chunk(document_id);
CREATE INDEX idx_chunk_tenant ON document_chunk(tenant_id);
CREATE INDEX idx_chunk_embedding_hnsw
  ON document_chunk USING hnsw (embedding halfvec_l2_ops);

-- Conversations
CREATE TABLE conversation (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  tenant_id     UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
  user_id       UUID NOT NULL REFERENCES "user"(id),
  title         TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_message (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
  tenant_id       UUID NOT NULL,   -- denormalized for RLS
  user_id         UUID NOT NULL,
  role            TEXT NOT NULL CHECK (role IN ('user','assistant')),
  content         TEXT NOT NULL,
  citations       JSONB,           -- [{docId, chunkId, score, title}]
  model           TEXT,
  prompt_tokens   INT,
  completion_tokens INT,
  latency_ms      INT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_msg_conv ON chat_message(conversation_id, created_at);

-- Audit log (append-only)
CREATE TABLE audit_log (
  id           BIGSERIAL PRIMARY KEY,
  tenant_id    UUID NOT NULL,
  user_id      UUID,
  action       TEXT NOT NULL,   -- DOC_UPLOAD, DOC_DELETE, MEMBER_ADD, QUERY, LOGIN, ...
  entity_type  TEXT,
  entity_id    UUID,
  detail       JSONB,
  ip_address   INET,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_tenant_time ON audit_log(tenant_id, created_at DESC);

-- Usage metering (per tenant, per day, for billing)
CREATE TABLE usage_meter (
  tenant_id        UUID NOT NULL,
  day              DATE NOT NULL,
  llm_tokens       BIGINT NOT NULL DEFAULT 0,
  embedding_calls  BIGINT NOT NULL DEFAULT 0,
  doc_pages        INT NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, day)
);

-- ===== Row-Level Security (tenant isolation, DB-enforced) =====
ALTER TABLE document        ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_chunk  ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversation    ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_message    ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log       ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_meter     ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_member   ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_document    ON document       USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE POLICY tenant_isolation_chunk       ON document_chunk USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE POLICY tenant_isolation_conversation ON conversation  USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE POLICY tenant_isolation_message     ON chat_message   USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE POLICY tenant_isolation_audit       ON audit_log      USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE POLICY tenant_isolation_usage       ON usage_meter    USING (tenant_id = current_setting('app.current_tenant')::uuid);
CREATE POLICY tenant_isolation_member      ON tenant_member  USING (tenant_id = current_setting('app.current_tenant')::uuid);
