CREATE TABLE delegation_rules (
    id UUID PRIMARY KEY,
    delegator_id UUID NOT NULL,
    delegate_id UUID NOT NULL,
    org_id UUID NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX uq_delegation_rules_delegator_id_is_active
    ON delegation_rules (delegator_id, is_active);
