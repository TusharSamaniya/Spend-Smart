-- SpendSmart demo seed (idempotent)
-- Creates:
--  - Organization: SpendSmart Demo Org (plan=business, base_currency=INR, state_code=27)
--  - Users: admin@spendsmart.in (admin), manager@spendsmart.in (manager), employee@spendsmart.in (employee)
-- Password for all users: Password@123 (BCrypt strength 10 via pgcrypto)

DO $$
DECLARE
  v_org_id UUID;
BEGIN
  -- Ensure pgcrypto exists (already created in Flyway V1, but safe to re-check)
  CREATE EXTENSION IF NOT EXISTS pgcrypto;

  -- 1) Organization
  SELECT id INTO v_org_id
  FROM organizations
  WHERE name = 'SpendSmart Demo Org'
  LIMIT 1;

  IF v_org_id IS NULL THEN
    INSERT INTO organizations (name, plan, base_currency, state_code)
    VALUES ('SpendSmart Demo Org', 'business', 'INR', '27')
    RETURNING id INTO v_org_id;
  END IF;

  -- 2) Users (create only if missing)
  IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@spendsmart.in') THEN
    INSERT INTO users (org_id, email, password_hash, role, default_currency)
    VALUES (
      v_org_id,
      'admin@spendsmart.in',
      crypt('Password@123', gen_salt('bf', 10)),
      'admin',
      'INR'
    );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'manager@spendsmart.in') THEN
    INSERT INTO users (org_id, email, password_hash, role, default_currency)
    VALUES (
      v_org_id,
      'manager@spendsmart.in',
      crypt('Password@123', gen_salt('bf', 10)),
      'manager',
      'INR'
    );
  END IF;

  IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'employee@spendsmart.in') THEN
    INSERT INTO users (org_id, email, password_hash, role, default_currency)
    VALUES (
      v_org_id,
      'employee@spendsmart.in',
      crypt('Password@123', gen_salt('bf', 10)),
      'employee',
      'INR'
    );
  END IF;
END $$;
