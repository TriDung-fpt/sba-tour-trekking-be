-- Retain the PayOS checkout URL so repeated client requests reuse the active attempt.
ALTER TABLE trekking.payment_attempts
    ADD COLUMN checkout_url TEXT;

COMMENT ON COLUMN trekking.payment_attempts.checkout_url IS
    'PayOS hosted checkout URL for an active attempt; never used as payment confirmation.';
