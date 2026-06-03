CREATE TABLE IF NOT EXISTS ingestion_quarantine (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    trade_date DATE,
    reason VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    source VARCHAR(20),
    run_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_ingestion_quarantine_symbol_date
    ON ingestion_quarantine(symbol, trade_date);

CREATE INDEX IF NOT EXISTS ix_ingestion_quarantine_run
    ON ingestion_quarantine(run_id);
