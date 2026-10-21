ALTER TABLE trekking.tours
    ADD COLUMN IF NOT EXISTS cancellation_refund_rules JSONB NOT NULL DEFAULT '[]'::JSONB
        CHECK (jsonb_typeof(cancellation_refund_rules) = 'array');
