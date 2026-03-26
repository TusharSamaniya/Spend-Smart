CREATE TABLE workflow_definitions (
    id UUID PRIMARY KEY,
    org_id UUID NOT NULL,
    name VARCHAR(128) NOT NULL,
    conditions JSONB NOT NULL,
    steps JSONB NOT NULL,
    escalation_hours INT DEFAULT 48,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_workflow_definitions_org_id_is_active
    ON workflow_definitions (org_id, is_active);
