INSERT INTO gst_rate_master (
    id,
    hsn_sac_code,
    description,
    category,
    gst_rate,
    supply_type,
    is_exempt,
    effective_from
) VALUES
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f001', '04010000', 'Fresh food and unprocessed essentials', 'Food & Groceries', 0.0000, 'GOODS', TRUE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f002', '99921000', 'Educational services', 'Education', 0.0000, 'SERVICES', TRUE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f003', '99931000', 'Healthcare services', 'Healthcare', 0.0000, 'SERVICES', TRUE, DATE '2017-07-01'),

    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f004', '21069010', 'Packaged food items', 'Food & Groceries', 0.0500, 'GOODS', FALSE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f005', '99641000', 'Transport services', 'Travel', 0.0500, 'SERVICES', FALSE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f006', '99631100', 'Economy hotel stays', 'Hospitality', 0.0500, 'SERVICES', FALSE, DATE '2017-07-01'),

    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f007', '99632100', 'Hotel accommodation under Rs.7500 per night', 'Hospitality', 0.1200, 'SERVICES', FALSE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f008', '99642200', 'Business class air travel', 'Travel', 0.1200, 'SERVICES', FALSE, DATE '2017-07-01'),

    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f009', '99831300', 'Software services', 'Software & SaaS', 0.1800, 'SERVICES', FALSE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f010', '99831400', 'IT services', 'IT Services', 0.1800, 'SERVICES', FALSE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f011', '99633200', 'Restaurants with AC', 'Dining', 0.1800, 'SERVICES', FALSE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f012', '99632200', 'Standard hotel stays above Rs.7500', 'Hospitality', 0.1800, 'SERVICES', FALSE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f013', '99821900', 'Professional services', 'Professional Services', 0.1800, 'SERVICES', FALSE, DATE '2017-07-01'),

    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f014', '98010010', 'Luxury goods', 'Luxury Purchases', 0.2800, 'GOODS', FALSE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f015', '99632300', 'Five-star hotels', 'Hospitality', 0.2800, 'SERVICES', FALSE, DATE '2017-07-01'),
    ('0e9c7a8f-d5c8-4ad2-93d9-451f4be1f016', '22021020', 'Aerated drinks', 'Beverages', 0.2800, 'GOODS', FALSE, DATE '2017-07-01');