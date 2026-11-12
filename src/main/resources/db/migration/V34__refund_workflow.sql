-- Refund workflow: machine-readable cancellation tiers, encrypted payout destination
-- and manual / PayOS payout tracking.

-- Refund tiers belong to a cancellation policy version, e.g.
-- [{"minHoursBeforeDeparture":168,"refundPercent":100},{"minHoursBeforeDeparture":72,"refundPercent":50}]
ALTER TABLE trekking.policy_versions ADD COLUMN refund_tiers JSONB;
ALTER TABLE trekking.policy_versions ADD CONSTRAINT policy_versions_refund_tiers_check
    CHECK (refund_tiers IS NULL OR jsonb_typeof(refund_tiers) = 'array');
COMMENT ON COLUMN trekking.policy_versions.refund_tiers IS
    'Bậc hoàn theo số giờ trước giờ khởi hành; chỉ dùng cho chính sách huỷ.';

ALTER TABLE trekking.refund_requests
    ADD COLUMN paid_amount_vnd BIGINT CHECK (paid_amount_vnd IS NULL OR paid_amount_vnd >= 0),
    ADD COLUMN refund_percent INTEGER CHECK (refund_percent IS NULL OR refund_percent BETWEEN 0 AND 100),
    ADD COLUMN hours_before_departure BIGINT,
    ADD COLUMN bank_bin VARCHAR(20),
    ADD COLUMN bank_name VARCHAR(120),
    ADD COLUMN recipient_name_ciphertext TEXT,
    ADD COLUMN recipient_account_ciphertext TEXT,
    ADD COLUMN recipient_account_last4 VARCHAR(4),
    ADD COLUMN payout_channel TEXT CHECK (payout_channel IS NULL OR payout_channel IN ('payos','bank_transfer')),
    ADD COLUMN payout_reference_id VARCHAR(100),
    ADD COLUMN payout_id VARCHAR(200),
    ADD COLUMN payout_state VARCHAR(50),
    ADD COLUMN payout_initiated_by BIGINT REFERENCES trekking.app_accounts(id),
    ADD COLUMN payout_last_checked_at TIMESTAMPTZ;
COMMENT ON COLUMN trekking.refund_requests.recipient_account_ciphertext IS
    'AES-256-GCM (v1:base64(nonce|ciphertext|tag)); xoá khi hoàn tất hoặc bị từ chối.';

-- At most one live refund request per booking; rejected/failed requests stay as history.
CREATE UNIQUE INDEX uq_refund_requests_live_booking ON trekking.refund_requests(booking_id)
    WHERE status IN ('requested', 'approved', 'processing', 'completed');
CREATE UNIQUE INDEX uq_refund_requests_payout_reference ON trekking.refund_requests(payout_reference_id)
    WHERE payout_reference_id IS NOT NULL;
CREATE INDEX ix_refund_requests_status_created ON trekking.refund_requests(status, created_at DESC);

-- PayOS payouts have no uploaded evidence; manual transfers may attach one.
ALTER TABLE trekking.customer_refunds ALTER COLUMN evidence_url DROP NOT NULL;
ALTER TABLE trekking.customer_refunds ADD COLUMN payout_id VARCHAR(200);
