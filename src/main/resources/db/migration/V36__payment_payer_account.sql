-- Payer bank account reported by the payment provider (PayOS webhook counterAccount* fields).
-- Refunds are locked to this account so money returns to its source.
-- Account number and holder name are AES-256-GCM ciphertexts written by the application.
ALTER TABLE trekking.customer_payments
    ADD COLUMN payer_bank_bin VARCHAR(20),
    ADD COLUMN payer_bank_name VARCHAR(120),
    ADD COLUMN payer_account_ciphertext TEXT,
    ADD COLUMN payer_name_ciphertext TEXT,
    ADD COLUMN payer_account_last4 VARCHAR(4);

-- True when the refund destination was taken from the verified payer account instead of customer input.
ALTER TABLE trekking.refund_requests
    ADD COLUMN recipient_from_payment BOOLEAN NOT NULL DEFAULT FALSE;
