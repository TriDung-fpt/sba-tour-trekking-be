
CREATE TABLE trekking.policy_version_revocations (
    policy_version_id BIGINT PRIMARY KEY REFERENCES trekking.policy_versions(id),
    revoked_by BIGINT NOT NULL REFERENCES trekking.app_accounts(id),
    revoked_at TIMESTAMPTZ NOT NULL,
    reason TEXT NOT NULL CHECK (btrim(reason) <> '')
);
