# Project rules (ratified — do not relitigate in-session)

Decision record: ../MoneyTalks/docs/decisions/2026-08-16-one-money-app.md · newest rulings: ../MoneyTalks/docs/decisions/LOG.md

@ECOSYSTEM.md

- **MarketLens is the single owner of market data and investment analytics** for the ecosystem (E3): OHLCV, indicators, corporate actions, calendars, data quality. The unifier consumes this service and never re-implements market data.
- **Do NOT grow personal-finance features here.** Purchases, cards, budgets, and the complete financial picture belong to the unifier. This repo answers "what is this security worth and how has it behaved," nothing wider.
- **Say daily/latest pricing, never real-time** — in docs, API copy, and dashboards — unless the infrastructure actually changes (honesty invariant, A6).
- **`YahooDailyProvider` is authorized (E4):** implement `MarketDataProvider` (`fetchDailyCandles` + `sourceName`) alongside `AlphaVantageDailyProvider`, because Alpha Vantage's free tier cannot serve arbitrary per-user holdings. Per-candle provenance rides the existing `sourceName()`. Accepted risks are recorded in E4 — Yahoo's terms do not sanction this access and the endpoints break periodically; consumers must cache last-known prices, label staleness, and fail closed rather than fabricate a number.
- **Open design item:** the unifier needs a latest-quote path for a *dynamic* per-user holdings set; ingestion today is fixed-watchlist daily. Not yet decided — propose, don't assume.
- Demo mode needs no PostgreSQL or API key: `./mvnw -Pdemo spring-boot:run`.
