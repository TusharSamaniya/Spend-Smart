-- Drop the old policy that throws an error when app.org_id is not set
DROP POLICY IF EXISTS org_isolation ON expenses;

-- Remove FORCE ROW LEVEL SECURITY so the postgres superuser (used by analytics-service)
-- can bypass RLS and read all org data. Expense-service still enforces isolation via
-- the app.org_id session variable set by RlsInterceptor.
ALTER TABLE expenses NO FORCE ROW LEVEL SECURITY;

-- Re-create policy using current_setting with missing_ok=true to avoid errors
-- when the variable is not set (returns NULL safely, which means the row is hidden).
CREATE POLICY org_isolation
ON expenses
USING (
    current_setting('app.org_id', true) IS NOT NULL
    AND org_id = current_setting('app.org_id', true)::uuid
);
