CREATE TABLE gst_rate_master (
    id UUID PRIMARY KEY,
    hsn_sac_code VARCHAR(8) NOT NULL,
    description VARCHAR(255),
    category VARCHAR(128),
    gst_rate NUMERIC(5,4),
    supply_type VARCHAR(10),
    is_exempt BOOLEAN DEFAULT FALSE,
    effective_from DATE
);

CREATE UNIQUE INDEX ux_gst_rate_master_hsn_sac_code
    ON gst_rate_master (hsn_sac_code);
