-- Extend company applications with details collected during partner sign-up.
ALTER TABLE trekking.companies
    ADD COLUMN IF NOT EXISTS business_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS founded_year INTEGER,
    ADD COLUMN IF NOT EXISTS tour_regions JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS safety_rules_accepted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS safety_rules_version VARCHAR(40);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'trekking.companies'::regclass
          AND conname = 'companies_founded_year_check'
    ) THEN
        ALTER TABLE trekking.companies
            ADD CONSTRAINT companies_founded_year_check
            CHECK (founded_year IS NULL OR founded_year BETWEEN 1900 AND 2100);
    END IF;
END $$;
