INSERT INTO categories (id, org_id, parent_id, name, level)
VALUES
    (gen_random_uuid(), NULL, NULL, 'Food', 1),
    (gen_random_uuid(), NULL, NULL, 'Travel', 1),
    (gen_random_uuid(), NULL, NULL, 'Business', 1),
    (gen_random_uuid(), NULL, NULL, 'Utilities', 1),
    (gen_random_uuid(), NULL, NULL, 'Healthcare', 1),
    (gen_random_uuid(), NULL, NULL, 'Shopping', 1),
    (gen_random_uuid(), NULL, NULL, 'Education', 1),
    (gen_random_uuid(), NULL, NULL, 'Entertainment', 1),
    (gen_random_uuid(), NULL, NULL, 'Finance', 1),
    (gen_random_uuid(), NULL, NULL, 'Other', 1);

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Restaurant'),
        ('Delivery'),
        ('Grocery'),
        ('Canteen'),
        ('Tea and Coffee')
) AS children(child_name)
WHERE c.name = 'Food' AND c.org_id IS NULL AND c.level = 1;

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Taxi'),
        ('Hotel'),
        ('Fuel'),
        ('Flight'),
        ('Train and Bus')
) AS children(child_name)
WHERE c.name = 'Travel' AND c.org_id IS NULL AND c.level = 1;

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Client Meeting'),
        ('Office Supplies'),
        ('Software Subscription'),
        ('Business Meals'),
        ('Team Event')
) AS children(child_name)
WHERE c.name = 'Business' AND c.org_id IS NULL AND c.level = 1;

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Electricity'),
        ('Water'),
        ('Internet'),
        ('Mobile'),
        ('Gas')
) AS children(child_name)
WHERE c.name = 'Utilities' AND c.org_id IS NULL AND c.level = 1;

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Doctor Consultation'),
        ('Medicines'),
        ('Diagnostics'),
        ('Hospitalization'),
        ('Insurance Premium')
) AS children(child_name)
WHERE c.name = 'Healthcare' AND c.org_id IS NULL AND c.level = 1;

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Clothing'),
        ('Electronics'),
        ('Home Essentials'),
        ('Personal Care'),
        ('Gifts')
) AS children(child_name)
WHERE c.name = 'Shopping' AND c.org_id IS NULL AND c.level = 1;

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Course Fee'),
        ('Books'),
        ('Stationery'),
        ('Exam Fee'),
        ('Online Learning')
) AS children(child_name)
WHERE c.name = 'Education' AND c.org_id IS NULL AND c.level = 1;

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Movies'),
        ('OTT Subscription'),
        ('Games'),
        ('Events'),
        ('Hobbies')
) AS children(child_name)
WHERE c.name = 'Entertainment' AND c.org_id IS NULL AND c.level = 1;

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Bank Charges'),
        ('Loan EMI'),
        ('Investment'),
        ('Tax Payment'),
        ('Wallet Top-up')
) AS children(child_name)
WHERE c.name = 'Finance' AND c.org_id IS NULL AND c.level = 1;

INSERT INTO categories (id, org_id, parent_id, name, level)
SELECT gen_random_uuid(), NULL, c.id, child_name, 2
FROM categories c
CROSS JOIN LATERAL (
    VALUES
        ('Donation'),
        ('Penalty'),
        ('Pet Care'),
        ('Maintenance'),
        ('Miscellaneous')
) AS children(child_name)
WHERE c.name = 'Other' AND c.org_id IS NULL AND c.level = 1;
