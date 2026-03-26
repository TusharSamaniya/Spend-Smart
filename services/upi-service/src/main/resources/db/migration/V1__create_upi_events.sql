CREATE TABLE IF NOT EXISTS upi_events (
    id UUID PRIMARY KEY,
    org_id UUID,
    user_id UUID,
    upi_ref_id VARCHAR(64) UNIQUE NOT NULL,
    vpa_sender VARCHAR(128),
    vpa_receiver VARCHAR(128),
    amount NUMERIC(14,2) NOT NULL,
    currency CHAR(3) DEFAULT 'INR',
    txn_timestamp TIMESTAMPTZ NOT NULL,
    resolved_merchant VARCHAR(255),
    expense_id UUID,
    source VARCHAR(20),
    raw_payload JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_upi_events_upi_ref_id
    ON upi_events (upi_ref_id);
