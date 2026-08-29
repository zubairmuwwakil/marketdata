# Providers, routing, and currency

**Read when:** adding or changing a market-data provider, or touching `price_candle`.
**Asserted by:** `ProviderResolutionTest`, `MarketDataProviderRegistryTest`.

## Routing is by purpose, not one global default

- Curated watchlist → **Alpha Vantage** (sanctioned, 25/day).
- Dynamic per-user symbols → **Yahoo** (E4, unsanctioned, has headroom).

**Both are daily closes.** The split is sanction and quota, never latency. Resolve
through `MarketDataProviderRegistry` by `(AssetClass, capability)`; never inject a
`MarketDataProvider` bean directly.

## Prices carry a currency or they carry nothing

`price_candle.currency` is nullable and null means *"the provider did not report one"*
— never *"assume USD"*. Consumers must refuse to sum figures whose currency they
cannot prove.

## Crypto

Ratified as MarketLens' on 2026-08-18, not yet built here. Planned: Binance public
data for history (`data.binance.vision`, no key, published for bulk download — a
better risk posture than Yahoo; note it quotes **USDT**, not USD), CoinGecko for spot.
The hub's CoinGecko path is on loan until ported.

Questrade was considered and rejected for pricing: per-user OAuth with a rotating
refresh token, requires a brokerage account, and its real value is holdings sync,
which is the hub's domain.
