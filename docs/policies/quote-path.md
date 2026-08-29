# The quote path (ADR 0003, E4)

**Read when:** touching `QuoteService`, the sweep, provider fan-out, or staleness.
**Asserted by:** `ProviderPropertiesTest`, `QuoteServiceTest`, `QuoteRefreshJobTest`.

`GET /api/v1/quotes?symbols=` serves an arbitrary symbol set: cache-first from
`price_candle`, staleness measured against the exchange calendar, bounded on-demand
fan-out, and **fail closed** — `UNAVAILABLE` with a null price, never a fabricated
one. Demand-registered symbols live in `tracked_symbol` (symbols only, never consumer
identity or quantities), separate from the curated `watchlist_symbol`.

## Five rules, each bought with an incident

1. **Every non-FRESH quote carries a `CAUSE_*` reason.** When fan-out resolves
   nothing, `buildQuote` falls back to the cached candle — and a cache hit served
   after a timeout is byte-identical to one served because the market genuinely has
   no newer close. That ambiguity hid a nightly failure at the consumer for weeks
   (`MoneyTalks/docs/decisions/LOG.md` 2026-08-27). Keep the vocabulary in sync with
   the consumer.
2. **Fan-out deadlines are sized for a cold start (45s), not interactive latency.** A
   *measured* 4-symbol fan-out took 7.74s against a hardcoded 8s deadline, so on a
   cold container it never finished. A background sweep that gives up early returns
   worse data, not faster data. `ProviderPropertiesTest` fails if anyone lowers it.
3. **`@Scheduled` is not a scheduler on a host that spins down.** A sleeping container
   fires no timers; the 22:30 UTC warm sweep had never once run in production.
   `POST /api/v1/admin/quote-sweep` is the load-bearing trigger, the timer a fallback.
4. **The sweep force-refreshes.** The cache-miss test is
   `cachedTradeDate.isBefore(expectedSession)`, so a cache holding a *wrong* candle
   for the *right* date cannot repair itself. A sweep that consults the cache is a
   no-op exactly when it matters.
5. **An in-progress session is not a close.** Yahoo's daily series includes the
   session still trading; accepting it stores an intraday price as the day's close
   *and* freezes it, since a candle dated today is never re-fetched today. Candles
   after `expectedSession` are discarded — a no-op for crypto, whose expected session
   is the current UTC day.
