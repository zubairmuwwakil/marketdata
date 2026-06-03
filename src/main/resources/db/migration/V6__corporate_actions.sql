CREATE TABLE IF NOT EXISTS corporate_action (
  id           BIGSERIAL PRIMARY KEY,
  symbol       VARCHAR(10)   NOT NULL,
  action_date  DATE          NOT NULL,
  action_type  VARCHAR(20)   NOT NULL,
  split_factor NUMERIC(10,6),
  dividend     NUMERIC(10,6),
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_corporate_action_symbol_date_type UNIQUE (symbol, action_date, action_type)
);

CREATE INDEX IF NOT EXISTS ix_corporate_action_symbol_date
  ON corporate_action(symbol, action_date DESC);