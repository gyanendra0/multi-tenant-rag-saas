#!/usr/bin/env bash
#
# test-worker.sh — end-to-end smoke test for the ingestion worker (Phase 7, step 3)
#
# Automates: login -> upload a document -> read its ids from the DB ->
# publish a `documents.ingest` message -> poll the DB until READY/FAILED ->
# print chunk count. Saves you copying ids by hand.
#
# PREREQUISITES (must already be running):
#   1. Docker infra:   (cd tenantrag && docker compose up -d)   # rabbitmq, redis, minio
#   2. rag-service:    (cd tenantrag/rag-service && uvicorn app.main:app --port 8100)
#   3. worker:         (cd tenantrag/worker && python -m app.main)   # <-- watch its logs
#   4. Java backend:   (cd tenantrag/backend && ./mvnw spring-boot:run)
#
# USAGE (from tenantrag/worker, with the worker's venv active so publish_test works):
#   ./test-worker.sh
#
# Override any of these via env vars:
#   EMAIL, PASSWORD, API_URL, PG_DB, PG_SUPERUSER, APP_ROLE, PGPASSWORD, FILE
#
set -euo pipefail

# ---- Config (override via env) ---------------------------------------------
API_URL="${API_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-owner@companya.com}"
PASSWORD="${PASSWORD:-StrongPassword123!}"
PG_DB="${PG_DB:-rag_saas}"
PG_SUPERUSER="${PG_SUPERUSER:-$USER}"   # macOS login role (superuser) for reads
APP_ROLE="${APP_ROLE:-rag_app}"         # non-superuser app role (RLS applies)
FILE="${FILE:-}"                        # optional: path to a file to upload

# psql helpers
PSQL=(psql -d "$PG_DB" -U "$PG_SUPERUSER" -tA)   # -tA = tuples only, unaligned

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
fail() { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

command -v curl  >/dev/null || fail "curl not found"
command -v python >/dev/null || command -v python3 >/dev/null || fail "python not found"
PY="$(command -v python || command -v python3)"

# ---- 0) Make a test file if none supplied ----------------------------------
CLEANUP_FILE=0
if [[ -z "$FILE" ]]; then
  FILE="$(mktemp -t ragtest).txt"
  CLEANUP_FILE=1
  cat >"$FILE" <<'EOF'
Multi-tenant RAG systems isolate each tenant's data using PostgreSQL row level
security. Every query runs with app.current_tenant set, so one tenant can never
read another tenant's documents or chunks. This file exists to test the
ingestion worker end to end: it should be downloaded from MinIO, parsed to text,
chunked, embedded by the rag-service, and stored in document_chunk with the
document flipped to READY.
EOF
fi
say "Using file: $FILE"

# ---- 1) Login --------------------------------------------------------------
say "Logging in as $EMAIL"
LOGIN_JSON="$(curl -sS -X POST "$API_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"

TOKEN="$(printf '%s' "$LOGIN_JSON" | "$PY" -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')" \
  || fail "login failed: $LOGIN_JSON"
[[ -n "$TOKEN" ]] || fail "no accessToken in login response"

# ---- 2) Upload a document --------------------------------------------------
say "Uploading document"
UPLOAD_JSON="$(curl -sS -X POST "$API_URL/api/documents" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$FILE")"

DOC_ID="$(printf '%s' "$UPLOAD_JSON" | "$PY" -c 'import sys,json; print(json.load(sys.stdin)["id"])')" \
  || fail "upload failed: $UPLOAD_JSON"
say "documentId = $DOC_ID"

# ---- 3) Read tenant_id, storage_key, mime_type, filename from the DB -------
say "Reading document row from Postgres"
ROW="$("${PSQL[@]}" -c \
  "SELECT tenant_id || '|' || storage_key || '|' || coalesce(mime_type,'application/octet-stream') || '|' || filename FROM document WHERE id='$DOC_ID';")"
[[ -n "$ROW" ]] || fail "document $DOC_ID not found in DB"

IFS='|' read -r TENANT_ID STORAGE_KEY MIME_TYPE FILENAME <<<"$ROW"
say "tenantId    = $TENANT_ID"
say "storageKey  = $STORAGE_KEY"
say "mimeType    = $MIME_TYPE"
say "filename    = $FILENAME"

# ---- 4) Publish the ingest message -----------------------------------------
say "Publishing documents.ingest message (worker should pick it up now)"
"$PY" -m app.publish_test "$DOC_ID" "$TENANT_ID" "$STORAGE_KEY" "$MIME_TYPE" "$FILENAME" \
  || fail "publish_test failed (is the worker venv active? is RabbitMQ up?)"

# ---- 5) Poll the document status until READY/FAILED ------------------------
say "Waiting for the worker to finish (polling status)..."
STATUS=""; CHUNKS=""
for i in $(seq 1 60); do
  OUT="$("${PSQL[@]}" -c \
    "SELECT status || '|' || coalesce(chunk_count::text,'') || '|' || coalesce(error_message,'') FROM document WHERE id='$DOC_ID';")"
  IFS='|' read -r STATUS CHUNKS ERR <<<"$OUT"
  printf '  [%02d] status=%s chunks=%s\n' "$i" "$STATUS" "$CHUNKS"
  if [[ "$STATUS" == "READY" || "$STATUS" == "FAILED" ]]; then break; fi
  sleep 2
done

# ---- 6) Verify chunks as the app role (so RLS applies) ---------------------
say "Counting document_chunk rows as $APP_ROLE (RLS-scoped)"
CHUNK_COUNT="$(PGPASSWORD="${PGPASSWORD:-1234}" psql -d "$PG_DB" -U "$APP_ROLE" -tA -c \
  "SELECT set_config('app.current_tenant','$TENANT_ID',true);
   SELECT count(*) FROM document_chunk WHERE document_id='$DOC_ID';" | tail -n1)"

# ---- Result ----------------------------------------------------------------
echo
if [[ "$STATUS" == "READY" ]]; then
  printf '\033[1;32m✅ PASS — status=READY, chunk_count=%s, document_chunk rows=%s\033[0m\n' "$CHUNKS" "$CHUNK_COUNT"
else
  printf '\033[1;31m❌ status=%s (error: %s)\033[0m\n' "$STATUS" "${ERR:-none}"
  echo "Check the worker terminal logs for the stack trace."
fi

[[ "$CLEANUP_FILE" == "1" ]] && rm -f "$FILE" || true
