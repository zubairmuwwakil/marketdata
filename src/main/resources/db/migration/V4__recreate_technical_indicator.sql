-- Recreate technical_indicator with normalized shape (one value per indicator_type)

DROP TABLE IF EXISTS technical_indicator;

CREATE TABLE technical_indicator (
  id              BIGSERIAL PRIMARY KEY,
  symbol          VARCHAR(10)   NOT NULL,
  trade_date      DATE          NOT NULL,
  indicator_type  VARCHAR(30)   NOT NULL,
  value           NUMERIC(19,6) NOT NULL,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_technical_indicator_symbol_date_type UNIQUE (symbol, trade_date, indicator_type)
);

CREATE INDEX IF NOT EXISTS ix_technical_indicator_symbol_date_type
  ON technical_indicator(symbol, trade_date, indicator_type);
