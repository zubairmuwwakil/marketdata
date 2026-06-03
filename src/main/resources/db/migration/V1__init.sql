CREATE TABLE IF NOT EXISTS price_candle (
  id           BIGSERIAL PRIMARY KEY,
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
  CONSTRAINT uq_price_candle_symbol_date UNIQUE (symbol, trade_date),
  CONSTRAINT ck_price_candle_price_order CHECK (low <= high),
  CONSTRAINT ck_price_candle_volume_nonneg CHECK (volume >= 0)
);

CREATE INDEX IF NOT EXISTS ix_price_candle_symbol_date
  ON price_candle(symbol, trade_date);

CREATE TABLE IF NOT EXISTS technical_indicator (
  id              BIGSERIAL PRIMARY KEY,
  symbol          VARCHAR(10)   NOT NULL,
  trade_date      DATE          NOT NULL,
  rsi_14          NUMERIC(10,4),
  macd            NUMERIC(19,6),
  macd_signal     NUMERIC(19,6),
  macd_histogram  NUMERIC(19,6),
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_technical_indicator_symbol_date UNIQUE (symbol, trade_date)
);

CREATE INDEX IF NOT EXISTS ix_technical_indicator_symbol_date
  ON technical_indicator(symbol, trade_date);

CREATE TABLE IF NOT EXISTS pipeline_run (
  id                BIGSERIAL PRIMARY KEY,
  run_date           DATE         NOT NULL,
  status             VARCHAR(20)  NOT NULL,
  symbols_expected   INTEGER      NOT NULL,
  symbols_processed  INTEGER      NOT NULL,
  error_message      TEXT,
  started_at         TIMESTAMPTZ  NOT NULL,
  finished_at        TIMESTAMPTZ,
  CONSTRAINT ck_pipeline_run_symbols_expected_nonneg CHECK (symbols_expected >= 0),
  CONSTRAINT ck_pipeline_run_symbols_processed_nonneg CHECK (symbols_processed >= 0),
  CONSTRAINT ck_pipeline_run_symbols_processed_lte_expected CHECK (symbols_processed <= symbols_expected)
);

CREATE INDEX IF NOT EXISTS ix_pipeline_run_started_at
  ON pipeline_run(started_at DESC);

CREATE INDEX IF NOT EXISTS ix_pipeline_run_run_date
  ON pipeline_run(run_date);