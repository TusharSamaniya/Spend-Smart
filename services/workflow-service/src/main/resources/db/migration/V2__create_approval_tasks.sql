CREATE TABLE approval_tasks (
    id UUID PRIMARY KEY,
    expense_id UUID NOT NULL,
    workflow_id UUID REFERENCES workflow_definitions(id),
    step_number INT NOT NULL,
    approver_id UUID NOT NULL,
    action VARCHAR(20) DEFAULT 'PENDING',
    comment TEXT,
    due_at TIMESTAMPTZ NOT NULL,
    acted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_approval_tasks_expense_id
    ON approval_tasks (expense_id);

CREATE INDEX idx_approval_tasks_approver_id_action
    ON approval_tasks (approver_id, action);
