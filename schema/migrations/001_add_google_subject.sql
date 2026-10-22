-- Apply after schema/schema.sql and before starting the Google-auth capable backend.
-- Existing password accounts keep a NULL subject and continue to use their passwords.
BEGIN;
ALTER TABLE trekking.app_accounts
    ADD COLUMN google_subject VARCHAR(255),
    ADD CONSTRAINT ck_accounts_google_subject
        CHECK (google_subject IS NULL OR btrim(google_subject) <> '');
CREATE UNIQUE INDEX uq_accounts_google_subject
    ON trekking.app_accounts (google_subject) WHERE google_subject IS NOT NULL;
COMMIT;
