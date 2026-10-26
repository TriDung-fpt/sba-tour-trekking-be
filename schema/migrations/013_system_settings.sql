-- Append-only versioned system settings: hold timeout, notification and AI configuration.
BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE TABLE trekking.system_settings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    setting_value TEXT NOT NULL CHECK (NULLIF(btrim(setting_value), '') IS NOT NULL),
    effective_from TIMESTAMPTZ NOT NULL,
    changed_by BIGINT NOT NULL REFERENCES trekking.app_accounts(id),
    change_reason TEXT NOT NULL CHECK (NULLIF(btrim(change_reason), '') IS NOT NULL),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (setting_key, version_number)
);
CREATE INDEX ix_system_settings_key_version ON trekking.system_settings(setting_key, version_number DESC);

COMMENT ON TABLE trekking.system_settings IS
    'Versioned system configuration; new versions only, old rows stay immutable.';

CREATE FUNCTION trekking.reject_system_setting_mutation() RETURNS trigger
LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'system_settings rows are append-only';
END;
$$;

CREATE TRIGGER trg_system_settings_append_only
    BEFORE UPDATE OR DELETE ON trekking.system_settings
    FOR EACH ROW EXECUTE FUNCTION trekking.reject_system_setting_mutation();

COMMIT;
