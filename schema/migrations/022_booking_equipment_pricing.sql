-- Equipment fees are per booking: (tour price per passenger * passengers) + equipment fee.
BEGIN;
SET LOCAL lock_timeout = '10s';

DROP VIEW IF EXISTS trekking.booking_money_totals;

ALTER TABLE trekking.bookings
    DROP COLUMN total_price_vnd;

ALTER TABLE trekking.bookings
    DROP COLUMN IF EXISTS pricing_version;

ALTER TABLE trekking.bookings
    ADD COLUMN total_price_vnd BIGINT GENERATED ALWAYS AS (
        participant_count::BIGINT * base_price_vnd + equipment_fee_vnd
    ) STORED;

CREATE VIEW trekking.booking_money_totals AS
SELECT b.id AS booking_id, b.total_price_vnd,
       COALESCE(p.received, 0) AS received_vnd,
       COALESCE(p.accepted, 0) AS accepted_vnd,
       COALESCE(r.refunded, 0) AS refunded_vnd
FROM trekking.bookings b
LEFT JOIN (SELECT booking_id, SUM(amount_vnd) AS received,
    SUM(amount_vnd) FILTER (WHERE allocation_status = 'accepted') AS accepted
    FROM trekking.customer_payments GROUP BY booking_id) p ON p.booking_id = b.id
LEFT JOIN (SELECT booking_id, SUM(amount_vnd) AS refunded
    FROM trekking.customer_refunds GROUP BY booking_id) r ON r.booking_id = b.id;
COMMENT ON VIEW trekking.booking_money_totals IS
'Actual customer receipts and refunds; aggregate lookup does not replace transactional payment and refund checks.';

COMMIT;
