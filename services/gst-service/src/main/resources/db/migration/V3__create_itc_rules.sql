CREATE TABLE itc_eligibility_rules (
    id UUID PRIMARY KEY,
    expense_category VARCHAR(128),
    is_eligible BOOLEAN,
    max_eligible_amount NUMERIC(14,2),
    blocked_reason VARCHAR(255),
    notes TEXT
);

INSERT INTO itc_eligibility_rules (
    id,
    expense_category,
    is_eligible,
    max_eligible_amount,
    blocked_reason,
    notes
) VALUES
    (
        '7c918e9c-8af6-4e7b-9b36-4c69f2fd3001',
        'Food and Beverage',
        FALSE,
        NULL,
        'Blocked under ITC restrictions for personal consumption and hospitality in normal business cases.',
        'ITC generally not available except specific statutory exceptions.'
    ),
    (
        '7c918e9c-8af6-4e7b-9b36-4c69f2fd3002',
        'Travel',
        TRUE,
        NULL,
        NULL,
        'Eligible when incurred wholly and exclusively for business purposes and supported by valid tax invoice.'
    ),
    (
        '7c918e9c-8af6-4e7b-9b36-4c69f2fd3003',
        'Business subscriptions',
        TRUE,
        NULL,
        NULL,
        'SaaS, software, and other business subscriptions are generally ITC eligible when used for taxable supplies.'
    ),
    (
        '7c918e9c-8af6-4e7b-9b36-4c69f2fd3004',
        'Personal Shopping',
        FALSE,
        NULL,
        'Blocked as non-business / personal expenditure.',
        'No ITC allowed for personal use purchases.'
    );
