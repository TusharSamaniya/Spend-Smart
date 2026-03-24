CREATE INDEX IF NOT EXISTS idx_exp_org_date ON expenses (org_id, expense_date DESC);

CREATE INDEX IF NOT EXISTS idx_exp_user ON expenses (user_id);

CREATE INDEX IF NOT EXISTS idx_exp_status ON expenses (org_id, status);
