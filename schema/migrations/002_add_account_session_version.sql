-- Apply after migration 001 and before deploying account administration.
-- Existing sessions stay valid at version zero. Blocking increments the version
-- atomically with the account status and audit, permanently invalidating old sessions.
BEGIN;
ALTER TABLE trekking.app_accounts
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_accounts_session_version CHECK (session_version >= 0);
COMMIT;
