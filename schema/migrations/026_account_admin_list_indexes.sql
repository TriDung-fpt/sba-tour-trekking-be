-- Indexes for the admin account list: status + default sort, and role filter.
BEGIN;
CREATE INDEX IF NOT EXISTS ix_accounts_status_created_at
    ON trekking.app_accounts (status, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_account_roles_role_account
    ON trekking.account_roles (role, account_id);
COMMIT;
