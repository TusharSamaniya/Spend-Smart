CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE receipts (
    id UUID PRIMARY KEY,
    org_id UUID NOT NULL,
    user_id UUID NOT NULL,
    s3_key TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'UPLOADING',
    merchant_name VARCHAR(255),
    amount NUMERIC(14,2),
    currency CHAR(3),
    receipt_date DATE,
    gst_amount NUMERIC(14,2),
    cgst NUMERIC(14,2),
    sgst NUMERIC(14,2),
    igst NUMERIC(14,2),
    hsn_sac_code VARCHAR(8),
    line_items JSONB,
    confidence_score NUMERIC(5,4),
    duplicate_of UUID,
    raw_ocr JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
);
