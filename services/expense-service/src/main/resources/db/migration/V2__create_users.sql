CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID REFERENCES organizations(id),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(15),
    role VARCHAR(20) DEFAULT 'employee',
    upi_ids TEXT[] DEFAULT ARRAY[]::TEXT[],
    default_currency CHAR(3) DEFAULT 'INR',
    created_at TIMESTAMPTZ DEFAULT now()
);
