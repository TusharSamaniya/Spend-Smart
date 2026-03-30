CREATE TABLE IF NOT EXISTS budgets (
    id UUID PRIMARY KEY,
    org_id UUID NOT NULL,
    name VARCHAR(128),
    category_id UUID,
    team_id UUID,
    amount NUMERIC(14,2) NOT NULL,
    period_type VARCHAR(20),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    alert_thresholds INTEGER[] DEFAULT ARRAY[75, 90, 100],
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_budgets_org_id ON budgets (org_id);
CREATE INDEX IF NOT EXISTS idx_budgets_org_dates ON budgets (org_id, start_date, end_date);
