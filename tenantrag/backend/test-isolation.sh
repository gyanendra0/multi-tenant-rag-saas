#!/usr/bin/env bash
#
# test-isolation.sh — End-to-end multi-tenant isolation test (Phase 3.21).
#
# Proves the full chain:
#   JWT -> tenant_id claim -> TenantContext -> PostgreSQL RLS -> only own rows.
#
# Requirements:
#   - App running:  cd tenantrag/backend && mvn spring-boot:run
#   - Tools:        curl, jq, psql   (brew install jq)
#
# Usage:
#   chmod +x test-isolation.sh
#   ./test-isolation.sh
#
# Optional environment overrides:
#   BASE_URL   (default http://localhost:8080)
#   DB_NAME    (default rag_saas)
#   PSQL_USER  (adds -U <user> to psql if set)
#   PGPASSWORD (standard libpq password var, if your DB needs one)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DB_NAME="${DB_NAME:-rag_saas}"

# Build the psql command (optionally with a user).
# NOTE: If you connect as a PostgreSQL SUPERUSER (or any role with BYPASSRLS),
# Row Level Security is bypassed entirely — even FORCE ROW LEVEL SECURITY does
# not apply. On macOS, `psql -d rag_saas` with no -U connects as your login
# role, which is usually a superuser. That is fine for SEEDING, but the RLS
# enforcement check (Step 8) MUST run as the non-superuser app role.
PSQL=(psql -d "$DB_NAME" -v ON_ERROR_STOP=1)
if [[ -n "${PSQL_USER:-}" ]]; then
  PSQL+=(-U "$PSQL_USER")
fi

# The application's DB role — RLS actually applies to this one.
# Override with APP_ROLE=... ; supply its password via PGPASSWORD if needed.
APP_ROLE="${APP_ROLE:-rag_app}"
APP_PSQL=(psql -d "$DB_NAME" -v ON_ERROR_STOP=1 -U "$APP_ROLE")

# Unique slugs/emails per run so re-running never hits a 409 conflict.
STAMP="$(date +%s)"
A_EMAIL="isoA_${STAMP}@test.com"
B_EMAIL="isoB_${STAMP}@test.com"
A_SLUG="iso-a-${STAMP}"
B_SLUG="iso-b-${STAMP}"
PASSWORD="StrongPassword123!"

pass=0
fail=0
check() { # check "label" "expected" "actual"
  if [[ "$2" == "$3" ]]; then
    echo "  ✅ PASS: $1 (got: $3)"; pass=$((pass+1))
  else
    echo "  ❌ FAIL: $1 (expected: $2, got: $3)"; fail=$((fail+1))
  fi
}

status() { # status METHOD URL [TOKEN]
  local method="$1" url="$2" token="${3:-}"
  if [[ -n "$token" ]]; then
    curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url" -H "Authorization: Bearer $token"
  else
    curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url"
  fi
}

echo "==> Base URL: $BASE_URL   DB: $DB_NAME"
echo "==> Step 1: Register two organizations"

REG_A=$(curl -s -X POST "$BASE_URL/api/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"$A_EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Iso A\",\"tenantName\":\"Iso A\",\"tenantSlug\":\"$A_SLUG\"}")
REG_B=$(curl -s -X POST "$BASE_URL/api/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"$B_EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Iso B\",\"tenantName\":\"Iso B\",\"tenantSlug\":\"$B_SLUG\"}")

A_TENANT=$(echo "$REG_A" | jq -r '.tenantId // empty')
A_USER=$(echo "$REG_A"   | jq -r '.userId // empty')
B_TENANT=$(echo "$REG_B" | jq -r '.tenantId // empty')
B_USER=$(echo "$REG_B"   | jq -r '.userId // empty')

if [[ -z "$A_TENANT" || -z "$B_TENANT" ]]; then
  echo "  ❌ Registration failed. Responses were:"
  echo "     A: $REG_A"
  echo "     B: $REG_B"
  exit 1
fi
echo "  A tenant=$A_TENANT user=$A_USER"
echo "  B tenant=$B_TENANT user=$B_USER"

echo "==> Step 2: Seed one document per tenant (RLS-scoped inserts)"
"${PSQL[@]}" >/dev/null <<SQL
SELECT set_config('app.current_tenant', '$A_TENANT', false);
INSERT INTO document (tenant_id, uploaded_by, title, filename, mime_type, size_bytes, storage_key, status)
VALUES ('$A_TENANT', '$A_USER', 'Iso A Doc', 'a.txt', 'text/plain', 10, 'seed/a.txt', 'READY');
SQL
"${PSQL[@]}" >/dev/null <<SQL
SELECT set_config('app.current_tenant', '$B_TENANT', false);
INSERT INTO document (tenant_id, uploaded_by, title, filename, mime_type, size_bytes, storage_key, status)
VALUES ('$B_TENANT', '$B_USER', 'Iso B Doc', 'b.txt', 'text/plain', 10, 'seed/b.txt', 'READY');
SQL
echo "  seeded."

echo "==> Step 3: Log in as each owner"
TOK_A=$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$A_EMAIL\",\"password\":\"$PASSWORD\"}" | jq -r '.accessToken // empty')
TOK_B=$(curl -s -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$B_EMAIL\",\"password\":\"$PASSWORD\"}" | jq -r '.accessToken // empty')
if [[ -z "$TOK_A" || -z "$TOK_B" ]]; then
  echo "  ❌ Login failed (no access token)."; exit 1
fi
echo "  tokens acquired."

echo "==> Step 4: Positive — each tenant sees only its own document"
A_TITLES=$(curl -s "$BASE_URL/api/documents" -H "Authorization: Bearer $TOK_A" | jq -r '[.content[].title] | join(",")')
B_TITLES=$(curl -s "$BASE_URL/api/documents" -H "Authorization: Bearer $TOK_B" | jq -r '[.content[].title] | join(",")')
check "A sees only its doc" "Iso A Doc" "$A_TITLES"
check "B sees only its doc" "Iso B Doc" "$B_TITLES"

echo "==> Step 5: Negative — A cannot READ B's document (expect 404)"
B_DOC=$(curl -s "$BASE_URL/api/documents" -H "Authorization: Bearer $TOK_B" | jq -r '.content[0].id')
check "A GET B's doc" "404" "$(status GET "$BASE_URL/api/documents/$B_DOC" "$TOK_A")"

echo "==> Step 6: Negative — A cannot DELETE B's document (expect 404), and it survives"
check "A DELETE B's doc" "404" "$(status DELETE "$BASE_URL/api/documents/$B_DOC" "$TOK_A")"
B_COUNT=$(curl -s "$BASE_URL/api/documents" -H "Authorization: Bearer $TOK_B" | jq -r '.content | length')
check "B's doc still present" "1" "$B_COUNT"

echo "==> Step 7: Negative — no token / bad token (expect 401)"
check "no token"  "401" "$(status GET "$BASE_URL/api/documents")"
check "bad token" "401" "$(status GET "$BASE_URL/api/documents" "not.a.jwt")"

echo "==> Step 8: Deep — RLS rejects a forged cross-tenant INSERT at the DB layer"
echo "    (runs as app role '$APP_ROLE'; superusers/BYPASSRLS roles would bypass RLS)"
set +e
FORGE_OUT=$("${APP_PSQL[@]}" 2>&1 <<SQL
SELECT set_config('app.current_tenant', '$A_TENANT', false);
INSERT INTO document (tenant_id, uploaded_by, title, filename, mime_type, size_bytes, storage_key)
VALUES ('$B_TENANT', '$A_USER', 'evil', 'x.txt', 'text/plain', 1, 'k');
SQL
)
set -e
if echo "$FORGE_OUT" | grep -qi "row-level security"; then
  echo "  ✅ PASS: forged INSERT blocked by RLS"; pass=$((pass+1))
else
  echo "  ❌ FAIL: forged INSERT was NOT blocked. Output:"; echo "     $FORGE_OUT"; fail=$((fail+1))
fi

echo
echo "==================== RESULT ===================="
echo "  PASSED: $pass    FAILED: $fail"
echo "================================================"
[[ "$fail" -eq 0 ]] || exit 1
