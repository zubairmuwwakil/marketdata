-- ADR 0003: latest-quote path for a dynamic, per-user symbol set.

-- 1. Currency on stored prices.
--
-- Invisible while the watchlist is four US tickers; load-bearing the moment the
-- symbol set is user-supplied. VFV.TO returns CAD and AAPL returns USD, and a
-- portfolio total that adds them is a fabricated number. Nullable on purpose:
-- NULL means "the provider did not report one", never "assume USD" — a price
-- without a currency cannot be summed, so consumers fail closed on it.
-- VARCHAR, not CHAR: spring.jpa.hibernate.ddl-auto is `validate` in production, and
-- Hibernate maps a String field to VARCHAR. A CHAR(3) column would compare as
-- Types.CHAR against the entity's Types.VARCHAR and fail schema validation at
-- startup — a failure that never appears in demo mode (H2, create-drop) or in any
-- unit test, only on deploy.
ALTER TABLE price_candle ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

-- Backfill is enumerated, not blanket. These five are the seeded watchlist and
-- each is verifiably a US listing quoted in USD, so stamping them is recording a
-- checked fact. A blanket UPDATE would stamp USD onto any symbol someone
-- backfilled, which is the exact class of invented data this column exists to
-- prevent. Everything else stays NULL until a provider reports its currency.
UPDATE price_candle
   SET currency = 'USD'
 WHERE currency IS NULL
   AND symbol IN ('MSFT', 'AAPL', 'NVDA', 'SPY', 'TSLA');

-- 2. Symbols arrive from users now, not from a curated list, and user symbols are
--    longer than the four-letter US tickers this column was sized for
--    (VFV.TO, BRK-B, BTCUSDT). Widening avoids a truncation failure on input we
--    do not control.
-- watchlist_symbol is already VARCHAR(20) from V2 and needs no change.
ALTER TABLE price_candle ALTER COLUMN symbol TYPE VARCHAR(20);
ALTER TABLE ingestion_quarantine ALTER COLUMN symbol TYPE VARCHAR(20);

-- 3. Demand-registered symbols.
--
-- Deliberately NOT watchlist_symbol: the two have different lifecycles (curated
-- vs. demand), different providers, and different quota. Conflating them would
-- put user-derived symbols into the watchlist dashboard and into the Alpha
-- Vantage 25-a-day loop.
--
-- Privacy, recorded rather than assumed: this table is the union of every symbol
-- any consumer has asked about. It holds SYMBOLS ONLY — no consumer identity, no
-- quantities, no user ids. MarketLens learns that someone asked about VFV.TO,
-- never whose portfolio it is in.
CREATE TABLE IF NOT EXISTS tracked_symbol (
    symbol             VARCHAR(20)  PRIMARY KEY,
    asset_class        VARCHAR(16)  NOT NULL DEFAULT 'EQUITY',
    first_requested_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_requested_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    request_count      BIGINT       NOT NULL DEFAULT 0,
    last_resolved_at   TIMESTAMPTZ,
    last_status        VARCHAR(20),
    CONSTRAINT ck_tracked_symbol_request_count_nonneg CHECK (request_count >= 0)
);

-- Drives both the nightly warm-up sweep (most-recently-wanted first) and
-- retirement of symbols nobody has asked about in a long time.
CREATE INDEX IF NOT EXISTS ix_tracked_symbol_last_requested
    ON tracked_symbol(last_requested_at DESC);
