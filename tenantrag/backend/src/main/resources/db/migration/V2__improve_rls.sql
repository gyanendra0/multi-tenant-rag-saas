-- V2__improve_rls.sql
--
-- Hardens the Row-Level Security (RLS) set up in V1.
--
-- Two problems with V1 are fixed here:
--
-- 1) MISSING "FORCE":
--    By default, the *owner* of a table BYPASSES RLS. If the application
--    connects as the table owner (e.g. rag_app owns these tables), the V1
--    policies do NOT actually restrict it and cross-tenant isolation is not
--    enforced. FORCE ROW LEVEL SECURITY makes RLS apply even to the owner.
--
-- 2) MISSING "WITH CHECK" + fragile cast:
--    V1 policies only had a USING clause. USING filters rows that are
--    read/updated/deleted, but it does NOT stop an INSERT (or an UPDATE that
--    changes tenant_id) from writing a row belonging to ANOTHER tenant.
--    WITH CHECK closes that hole.
--
--    V1 also used current_setting('app.current_tenant')::uuid directly. If the
--    setting is empty or unset, casting '' to uuid throws an error. Using
--    NULLIF(current_setting('app.current_tenant', true), '')::uuid is robust:
--      - the second arg `true` = "missing_ok", returns NULL instead of erroring
--      - NULLIF(..., '') turns an empty string into NULL
--      - a NULL comparison yields no rows (safe default: see nothing)
--
-- NOTE: We must NEVER edit V1__init.sql after it has been applied. All schema
--       changes go in new, forward-only migrations like this one.

-- ---------------------------------------------------------------------------
-- 1) Force RLS so the table owner is also subject to the policies.
-- ---------------------------------------------------------------------------
ALTER TABLE document        FORCE ROW LEVEL SECURITY;
ALTER TABLE document_chunk  FORCE ROW LEVEL SECURITY;
ALTER TABLE conversation    FORCE ROW LEVEL SECURITY;
ALTER TABLE chat_message    FORCE ROW LEVEL SECURITY;
ALTER TABLE audit_log       FORCE ROW LEVEL SECURITY;
ALTER TABLE usage_meter     FORCE ROW LEVEL SECURITY;
ALTER TABLE tenant_member   FORCE ROW LEVEL SECURITY;

-- ---------------------------------------------------------------------------
-- 2) Replace each V1 policy with one that has BOTH USING and WITH CHECK,
--    using the robust NULLIF(...) form.
--
--    DROP POLICY IF EXISTS makes this migration safe to re-run and independent
--    of the exact V1 policy names (they match V1, but IF EXISTS is defensive).
-- ---------------------------------------------------------------------------

-- document -------------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation_document ON document;
CREATE POLICY tenant_isolation_document ON document
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

-- document_chunk -------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation_chunk ON document_chunk;
CREATE POLICY tenant_isolation_chunk ON document_chunk
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

-- conversation ---------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation_conversation ON conversation;
CREATE POLICY tenant_isolation_conversation ON conversation
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

-- chat_message ---------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation_message ON chat_message;
CREATE POLICY tenant_isolation_message ON chat_message
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

-- audit_log ------------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation_audit ON audit_log;
CREATE POLICY tenant_isolation_audit ON audit_log
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

-- usage_meter ----------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation_usage ON usage_meter;
CREATE POLICY tenant_isolation_usage ON usage_meter
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );

-- tenant_member --------------------------------------------------------------
DROP POLICY IF EXISTS tenant_isolation_member ON tenant_member;
CREATE POLICY tenant_isolation_member ON tenant_member
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
    );
