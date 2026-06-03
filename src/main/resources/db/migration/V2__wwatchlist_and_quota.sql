CREATE TABLE IF NOT EXISTS watchlist_symbol (
  id          BIGSERIAL PRIMARY KEY,
  symbol      VARCHAR(20) NOT NULL UNIQUE,
  active      BOOLEAN     NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS api_quota_usage (
  id            BIGSERIAL PRIMARY KEY,
  provider      VARCHAR(30) NOT NULL,
  usage_date    DATE        NOT NULL,
  calls_used    INTEGER     NOT NULL DEFAULT 0,
  calls_limit   INTEGER     NOT NULL,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_quota_provider_date UNIQUE (provider, usage_date),
  CONSTRAINT ck_calls_used_nonneg CHECK (calls_used >= 0),
  CONSTRAINT ck_calls_limit_pos CHECK (calls_limit > 0),
  CONSTRAINT ck_used_lte_limit CHECK (calls_used <= calls_limit)
);