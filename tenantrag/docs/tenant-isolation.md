# Tenant Isolation (Row‑Level Security)

The #1 rule of a multi‑tenant SaaS: **Tenant A must never see Tenant B's data.**
We enforce this at the **database** layer with PostgreSQL Row‑Level Security
(RLS), so a bug in application code can't leak data across tenants.

## The core idea

1. Every tenant‑scoped table has a `tenant_id` column.
2. Each such table has an RLS **policy**: "a row is visible only if
   `tenant_id = current_setting('app.current_tenant')`".
3. Before running any query, the backend sets that variable **for the current
   transaction only**:

   ```sql
   SELECT set_config('app.current_tenant', '<tenantId>', true);
   ```
   The `true` = "transaction‑local", so it never leaks to another request that
   reuses the same pooled connection.

## Why the app role matters

- The backend connects as **`rag_app`**, a **non‑superuser** role.
- RLS is **bypassed by superusers**. So we must *not* connect as one.
- We also use `FORCE ROW LEVEL SECURITY` on the tables so even the table owner
  is subject to the policy.

> **Lesson learned:** early on, connecting as the macOS login role (a superuser)
> silently bypassed RLS and isolation "passed" for the wrong reason. Always test
> isolation as the `rag_app` role.

## Reads *and* writes are protected

Policies use both:
- `USING (...)` → controls which rows are **visible** (SELECT/UPDATE/DELETE).
- `WITH CHECK (...)` → controls which rows can be **inserted/updated** — stops a
  request from writing a row belonging to another tenant.

## Example policy (conceptual)

```sql
ALTER TABLE document ENABLE ROW LEVEL SECURITY;
ALTER TABLE document FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON document
  USING      (tenant_id = current_setting('app.current_tenant')::uuid)
  WITH CHECK (tenant_id = current_setting('app.current_tenant')::uuid);
```

## How the backend applies it

A transaction helper (`TenantTransactionService`) wraps work so that every unit
of DB work runs inside a transaction that first calls `set_config`. This covers
JPA/Hibernate queries **and** raw JDBC (like the vector search and chat inserts).

## The worker does it too

The Python worker connects as `rag_app` and runs the same
`set_config('app.current_tenant', ...)` before writing chunks — so ingestion is
just as isolated as the API.

## Vector search stays isolated

The cosine search is raw SQL:

```sql
SELECT id, 1 - (embedding <=> ?::halfvec) AS score
FROM document_chunk
ORDER BY embedding <=> ?::halfvec
LIMIT ?;
```

Because it runs inside the tenant transaction, RLS automatically restricts it to
the current tenant's chunks — no manual `WHERE tenant_id = ?` needed (though the
policy effectively adds it).
