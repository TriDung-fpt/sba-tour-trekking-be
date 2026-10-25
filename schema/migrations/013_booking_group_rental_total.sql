-- Booking equipment rental is charged once for the whole group.
BEGIN;
SET LOCAL lock_timeout = '10s';

DROP VIEW trekking.booking_money_totals;

ALTER TABLE trekking.bookings
    ADD COLUMN group_rental_fee_vnd BIGINT CHECK (group_rental_fee_vnd BETWEEN 0 AND 1000000000000);

ALTER TABLE trekking.bookings DROP COLUMN total_price_vnd;
ALTER TABLE trekking.bookings
    ADD COLUMN total_price_vnd BIGINT GENERATED ALWAYS AS
        (participant_count::BIGINT * base_price_vnd
            + COALESCE(group_rental_fee_vnd, participant_count::BIGINT * equipment_fee_vnd)) STORED;

COMMENT ON COLUMN trekking.bookings.group_rental_fee_vnd IS
    'Total VND rental fee for the whole booking group on bookings created with group rental pricing.';
COMMENT ON COLUMN trekking.bookings.total_price_vnd IS
    'Generated total: per-person schedule price times participant count, plus group rental fee. NULL group fee preserves the legacy per-person equipment fee formula.';

CREATE VIEW trekking.booking_money_totals AS
SELECT b.id AS booking_id, b.total_price_vnd,
       COALESCE(p.received, 0) AS received_vnd,
       COALESCE(p.accepted, 0) AS accepted_vnd,
       COALESCE(r.refunded, 0) AS refunded_vnd
FROM trekking.bookings b
LEFT JOIN (
    SELECT booking_id, SUM(amount_vnd) AS received,
        SUM(amount_vnd) FILTER (WHERE allocation_status = 'accepted') AS accepted
    FROM trekking.customer_payments GROUP BY booking_id
) p ON p.booking_id = b.id
LEFT JOIN (
    SELECT booking_id, SUM(amount_vnd) AS refunded
    FROM trekking.customer_refunds GROUP BY booking_id
) r ON r.booking_id = b.id;
COMMENT ON VIEW trekking.booking_money_totals IS
    'Actual customer receipts and refunds; aggregate lookup does not replace transactional payment and refund checks.';

COMMIT;
