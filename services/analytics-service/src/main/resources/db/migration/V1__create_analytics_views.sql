-- ============================================================================
-- Analytics Materialized Views
-- Creates materialized views for analytics aggregations.
-- ============================================================================

-- mv_daily_spend: aggregates expenses by org, category, and day
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_spend AS
SELECT
    e.org_id,
    e.category_id,
    e.expense_date,
    COALESCE(SUM(e.amount_base), 0)::NUMERIC(14,2) AS total,
    COUNT(*)::BIGINT AS txn_count
FROM expenses e
WHERE e.deleted_at IS NULL
GROUP BY e.org_id, e.category_id, e.expense_date
WITH DATA;

-- Unique index required for REFRESH MATERIALIZED VIEW CONCURRENTLY
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_daily_spend_pk
    ON mv_daily_spend (org_id, category_id, expense_date)
    WHERE category_id IS NOT NULL;

-- Non-unique index for rows where category_id is NULL
CREATE INDEX IF NOT EXISTS idx_mv_daily_spend_org_date
    ON mv_daily_spend (org_id, expense_date);

-- mv_budget_utilization: shows each budget with its current spend percentage
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_budget_utilization AS
SELECT
    b.id AS budget_id,
    b.org_id,
    b.name,
    b.amount AS budget_amount,
    COALESCE(SUM(e.amount_base), 0)::NUMERIC(14,2) AS spent,
    CASE
        WHEN b.amount > 0
        THEN ROUND((COALESCE(SUM(e.amount_base), 0) / b.amount) * 100, 2)
        ELSE 0
    END::NUMERIC(7,2) AS pct_used
FROM budgets b
LEFT JOIN expenses e
    ON e.org_id = b.org_id
    AND (b.category_id IS NULL OR e.category_id = b.category_id)
    AND e.expense_date BETWEEN b.start_date AND b.end_date
    AND e.deleted_at IS NULL
    AND e.status NOT IN ('REJECTED', 'DRAFT')
GROUP BY b.id, b.org_id, b.name, b.amount
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_budget_utilization_pk
    ON mv_budget_utilization (budget_id);
