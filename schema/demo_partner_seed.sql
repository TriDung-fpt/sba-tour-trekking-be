-- Local demo partner account for exercising the partner tour workspace.
-- Prerequisite: run demo_tour_seed.sql to create the approved WeTrek demo company.
-- Run from the repository root:
--   docker cp schema/demo_partner_seed.sql trekking-postgres:/tmp/demo_partner_seed.sql
--   docker exec trekking-postgres psql -U postgres -d trekking_db -v ON_ERROR_STOP=1 -f /tmp/demo_partner_seed.sql
-- Demo login: partner.demo@wetrek.test / TrekPartner2026!

BEGIN;
SET LOCAL search_path TO trekking, public;

INSERT INTO app_accounts (email, password_hash, full_name, status, email_verified_at)
SELECT 'partner.demo@wetrek.test',
       '$2a$12$6f2NVXU7ifqgLQyVJAeeeeBcmot/XxthDKZ8BBdJSFFihDTKOMu66',
       'Đối tác WeTrek Demo', 'active', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM app_accounts WHERE lower(btrim(email)) = 'partner.demo@wetrek.test'
);

INSERT INTO account_roles (account_id, role)
SELECT id, 'company_manager'
FROM app_accounts
WHERE lower(btrim(email)) = 'partner.demo@wetrek.test'
ON CONFLICT (account_id, role) DO NOTHING;

INSERT INTO company_members (company_id, account_id, member_role, active)
SELECT c.id, a.id, 'owner', TRUE
FROM companies c
JOIN app_accounts a ON lower(btrim(a.email)) = 'partner.demo@wetrek.test'
WHERE c.tax_code = 'DEMO-WETREK-001'
ON CONFLICT (company_id, account_id) DO UPDATE SET
    member_role = 'owner',
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

COMMIT;
