-- V3__member_self_access.sql
--
-- Enables the LOGIN flow to resolve which tenant(s) a user belongs to,
-- WITHOUT weakening tenant isolation and WITHOUT needing BYPASSRLS or a
-- SECURITY DEFINER function.
--
-- THE PROBLEM
-- -----------
-- `tenant_member` has FORCE ROW LEVEL SECURITY (see V2). Its policy only allows
-- rows where tenant_id = app.current_tenant. But at LOGIN we don't know the
-- tenant yet — resolving the tenant is the whole point of logging in. So the
-- app cannot read the user's memberships: chicken-and-egg.
--
-- THE SOLUTION
-- ------------
-- PostgreSQL combines multiple PERMISSIVE policies with OR. We add a second,
-- SELECT-only policy that lets a request read a membership row when it matches
-- the *current user* (a new GUC `app.current_user_id`), regardless of tenant.
--
-- The login flow is:
--   1. SELECT user by email        (the "user" table is NOT RLS-protected)
--   2. verify the BCrypt password
--   3. set_config('app.current_user_id', <user_id>, true)   -- transaction-local
--   4. SELECT memberships          -- now allowed by THIS policy only
--
-- Because login sets ONLY app.current_user_id (not app.current_tenant), a user
-- can read ONLY their own membership rows — never anyone else's.
--
-- WHY "FOR SELECT" ONLY
-- ---------------------
-- This policy grants read access only. INSERT/UPDATE/DELETE are still governed
-- solely by the tenant-scoped policy from V2 (with its WITH CHECK), so this does
-- NOT open a write hole.

CREATE POLICY member_self_access ON tenant_member
    FOR SELECT
    USING (
        user_id = NULLIF(current_setting('app.current_user_id', true), '')::uuid
    );
