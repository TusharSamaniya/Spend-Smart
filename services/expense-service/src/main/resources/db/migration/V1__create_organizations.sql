CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    plan VARCHAR(20) DEFAULT 'free',
    base_currency CHAR(3) DEFAULT 'INR',
    gstin VARCHAR(15),
    state_code CHAR(2),
    created_at TIMESTAMPTZ DEFAULT now()
);
