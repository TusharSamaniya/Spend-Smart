ALTER TABLE expenses ENABLE ROW LEVEL SECURITY;

CREATE POLICY org_isolation
ON expenses
USING (org_id = current_setting('app.org_id')::uuid);

ALTER TABLE expenses FORCE ROW LEVEL SECURITY;
