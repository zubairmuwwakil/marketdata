DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'price_candle'
          AND table_type = 'BASE TABLE'
    ) THEN
        ALTER TABLE price_candle RENAME TO price_candle_old;

      ALTER TABLE price_candle_old DROP CONSTRAINT IF EXISTS uq_price_candle_symbol_date;
      ALTER TABLE price_candle_old DROP CONSTRAINT IF EXISTS ck_price_candle_price_order;
      ALTER TABLE price_candle_old DROP CONSTRAINT IF EXISTS ck_price_candle_volume_nonneg;

        CREATE TABLE price_candle (
          symbol       VARCHAR(10)   NOT NULL,
          trade_date   DATE          NOT NULL,
          open         NUMERIC(19,6) NOT NULL,
          high         NUMERIC(19,6) NOT NULL,
          low          NUMERIC(19,6) NOT NULL,
          close        NUMERIC(19,6) NOT NULL,
          volume       BIGINT        NOT NULL,
          adjusted     BOOLEAN       NOT NULL DEFAULT FALSE,
          source       VARCHAR(20)   NOT NULL DEFAULT 'FINNHUB',
          created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
          CONSTRAINT pk_price_candle PRIMARY KEY (symbol, trade_date),
          CONSTRAINT ck_price_candle_price_order CHECK (low <= high),
          CONSTRAINT ck_price_candle_volume_nonneg CHECK (volume >= 0)
        ) PARTITION BY RANGE (trade_date);

        CREATE TABLE IF NOT EXISTS price_candle_2020 PARTITION OF price_candle
          FOR VALUES FROM ('2020-01-01') TO ('2021-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2021 PARTITION OF price_candle
          FOR VALUES FROM ('2021-01-01') TO ('2022-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2022 PARTITION OF price_candle
          FOR VALUES FROM ('2022-01-01') TO ('2023-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2023 PARTITION OF price_candle
          FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2024 PARTITION OF price_candle
          FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2025 PARTITION OF price_candle
          FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2026 PARTITION OF price_candle
          FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2027 PARTITION OF price_candle
          FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2028 PARTITION OF price_candle
          FOR VALUES FROM ('2028-01-01') TO ('2029-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2029 PARTITION OF price_candle
          FOR VALUES FROM ('2029-01-01') TO ('2030-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_2030 PARTITION OF price_candle
          FOR VALUES FROM ('2030-01-01') TO ('2031-01-01');
        CREATE TABLE IF NOT EXISTS price_candle_default PARTITION OF price_candle
          DEFAULT;

        CREATE INDEX IF NOT EXISTS ix_price_candle_symbol_date
          ON price_candle(symbol, trade_date);

        INSERT INTO price_candle (symbol, trade_date, open, high, low, close, volume, adjusted, source, created_at)
        SELECT symbol, trade_date, open, high, low, close, volume, adjusted, source, created_at
        FROM price_candle_old;

        DROP TABLE price_candle_old;
    END IF;
END $$;