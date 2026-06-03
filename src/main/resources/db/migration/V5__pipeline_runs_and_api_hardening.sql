ALTER TABLE pipeline_run
    ADD COLUMN IF NOT EXISTS run_type VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    ADD COLUMN IF NOT EXISTS symbols_failed INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS requested_from DATE,
    ADD COLUMN IF NOT EXISTS requested_to DATE,
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64),
    ADD COLUMN IF NOT EXISTS started_by VARCHAR(60),
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'ix_pipeline_run_idempotency'
    ) THEN
        CREATE UNIQUE INDEX ix_pipeline_run_idempotency ON pipeline_run(idempotency_key)
            WHERE idempotency_key IS NOT NULL;
    END IF;
END $$;

ALTER TABLE pipeline_run
    DROP CONSTRAINT IF EXISTS ck_pipeline_run_symbols_processed_lte_expected;

ALTER TABLE pipeline_run
    ADD CONSTRAINT ck_pipeline_run_symbols_processed_lte_expected
        CHECK (symbols_processed <= symbols_expected);