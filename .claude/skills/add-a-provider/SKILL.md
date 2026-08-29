---
name: add-a-provider
description: Use when adding or changing a market-data provider, provider routing, quotas, or MarketDataProviderRegistry.
---

# Adding a market-data provider

Read [`docs/policies/providers.md`](../../../docs/policies/providers.md) first.

## Rules

- Register, do not inject. Resolve through `MarketDataProviderRegistry` by
  `(AssetClass, capability)`; direct `MarketDataProvider` injection is guarded
  against.
- Route by purpose: the curated watchlist uses sanctioned, quota-limited sources;
  dynamic symbols use sources with headroom. Both are daily closes, never a
  latency split.
- Preserve reported currency. Never default an unknown currency to USD; Binance
  prices are USDT, not USD.
- Do not accept a still-trading session as a daily close.
- Provider credentials are per-request only; never persist or log them. Read
  [`api-keys.md`](../../../docs/policies/api-keys.md) when touching BYOK.

## Delivery

1. Implement `MarketDataProvider`, plus `LatestQuoteProvider` when appropriate.
2. Register the provider and add focused tests beside the existing ingestion tests.
3. Preserve the standalone demo: it must continue to need neither PostgreSQL nor
   a provider key. Do not make demo depend on an external provider.
4. Run `./mvnw --batch-mode verify` and `./mvnw --batch-mode -Pdemo test`.
