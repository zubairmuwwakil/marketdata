# ADR 0003: Latest-Quote Path for a Dynamic Holdings Set

## Status

Accepted — 2026-08-18. Implements the deferred design item named in Amendment E4
of `../MoneyTalks/docs/decisions/2026-08-16-one-money-app.md`.

## Context

MarketLens ingests a **fixed** watchlist on a daily schedule: `IngestionService`
iterates `watchlist_symbol WHERE active`, spends one Alpha Vantage call per
symbol against a hard 25/day quota, and writes `price_candle`.

The unifier (MoneyTalks) tracks a user's **holdings**, which are a dynamic,
per-user, unbounded set. There was no path for it to ask *"what are these N
symbols worth as of the latest close."* Two things were missing, not one:

1. a **provider** that can serve arbitrary symbols (Alpha Vantage's free tier
   cannot — 25 calls/day is a fixed-watchlist budget), and
2. a **read path** whose symbol set is not the curated watchlist.

### Facts established by probing the live endpoints on 2026-08-18

These drove the design and are recorded so the next agent does not re-derive them:

| Endpoint | Result | Consequence |
|---|---|---|
| `GET /v8/finance/chart/{symbol}?interval=1d` | **HTTP 200**, plain `User-Agent`, **no cookie/crumb** | A pure-Java `RestClient` works. No `yfinance` sidecar, no browser emulation. |
| `GET /v7/finance/quote?symbols=A,B` | **HTTP 401 Unauthorized** | **There is no working batch endpoint.** N symbols = N requests. The naive "one call values the whole portfolio" design is impossible. |
| `/v8/finance/chart/ZZZZNOTREAL` | **HTTP 404**, `"No data found, symbol may be delisted"` | Delisted/typo is cleanly separable from throttling — maps onto the existing `ExternalServiceException(retryable)` split. |
| `v8` `meta` block | carries `currency`, `instrumentType`, `exchangeTimezoneName` | Per-quote currency is available for free, and we need it (below). |

### The currency gap (found during this work, not in the record)

`price_candle` has **no currency column** (`V1__init.sql`), and neither does the
hub's `Holding.lastPriceMinor`. That is invisible while the watchlist is four US
tickers. It stops being invisible the moment the symbol set is user-supplied:
`VFV.TO` returns CAD and `AAPL` returns USD, and a portfolio total that adds them
is a fabricated number — exactly what E4's invariant and the A6 honesty invariant
forbid. Yahoo hands us `meta.currency`; Binance (crypto, next session) quotes
**USDT**, which is *approximately* USD and not identically USD. So currency is
load-bearing, not decorative.

## Decision

### 1. Provider routing is by *purpose*, not one global default

| Path | Symbol set | Provider | Rationale |
|---|---|---|---|
| Curated watchlist ingestion | fixed, small | **Alpha Vantage** | Sanctioned source; 25/day is ample for a fixed list; keeps the portfolio-demo pipeline on licensed data. |
| Demand quote path | dynamic, per-user, unbounded | **Yahoo** (`YahooDailyProvider`, E4) | Alpha Vantage's free tier cannot serve it; Yahoo's headroom can. |

**The two paths do not differ in latency.** Both are daily closes. Alpha Vantage's
free tier is not real-time either, and at 25 calls/day nothing dynamic is possible
on it. What differs is **sanction and quota**: Alpha Vantage is licensed-but-tiny,
Yahoo is unlicensed-but-large. Per A6/E3 this is never described as real-time in
code, docs, or UI.

### 2. Capability-scoped provider interfaces + a registry

`MarketDataProvider` stays as the historical seam but is split by capability:

- `DailyCandleProvider` — `fetchDailyCandles(symbol, from, to)` (history)
- `LatestQuoteProvider` — `fetchLatestCandle(symbol)` (spot/last close)

`MarketDataProviderRegistry` resolves a provider by `(AssetClass, capability)`.

*Why not one flat interface:* the planned providers genuinely differ in what they
can do — Binance has crypto history but no equities, CoinGecko has spot but weak
free history, Questrade needs per-user OAuth. A single `fetchDailyCandles` forces
either a lowest-common-denominator interface or a leaky one. Capability scoping
keeps providers swappable **and** honest about coverage. This is also the concrete
mechanism for "swap providers later without touching callers."

A registry is additionally *required*, not merely nice: `IngestionService` injects
a single `MarketDataProvider` bean today, so adding a second `@Service` under
`@Profile("!demo")` fails startup with `NoUniqueBeanDefinition`.

### 3. Per-provider quota

`QuotaService` is hard-coded to `PROVIDER = "ALPHAVANTAGE"`, `DAILY_LIMIT = 25`.
Left alone, Yahoo requests would silently burn Alpha Vantage's quota and starve
the curated pipeline. Quota becomes provider-keyed (the `api_quota_usage` table
already has a `provider` column). Yahoo's budget is a **self-imposed politeness
cap**, not a vendor-published limit — named as such, because we are the ones
choosing to be well-behaved against an endpoint whose terms do not sanction us.

### 4. Demand-registered symbols (`tracked_symbol`)

A symbol arriving on the quote path auto-registers in `tracked_symbol` with
`last_requested_at`. This is deliberately **separate from `watchlist_symbol`**:
the two have different lifecycles (curated vs. demand), different providers, and
different quota. Conflating them would put user-derived symbols into the
watchlist dashboard and into the Alpha Vantage quota loop.

Candles for demand symbols persist in `price_candle` as normal — that is what
makes "cache last-known" survive restarts and therefore what makes the E4
invariant real rather than decorative.

*Recorded privacy consequence:* MarketLens's database becomes the union of every
symbol any consumer has asked about. It stores **symbols only** — never
quantities, never book cost, never a user id, never anything tying a symbol to a
person. `tracked_symbol` rows unrequested for `retention.tracked-symbol-days`
are retired by the existing retention service.

### 5. Read contract

```
GET /api/v1/quotes?symbols=AAPL,VFV.TO,ZZZZ    (role: USER or ADMIN)
```

```json
{
  "pricing": "daily-close",
  "expectedSession": "2026-08-17",
  "quotes": [
    {"symbol":"AAPL","status":"FRESH","close":"310.03","currency":"USD",
     "tradeDate":"2026-08-17","source":"YAHOO","staleTradingDays":0},
    {"symbol":"VFV.TO","status":"STALE","close":"142.30","currency":"CAD",
     "tradeDate":"2026-08-11","source":"YAHOO","staleTradingDays":4},
    {"symbol":"ZZZZ","status":"UNAVAILABLE","close":null,"currency":null,
     "tradeDate":null,"source":null,"reason":"no_data"}
  ]
}
```

`status` is the honesty mechanism:

- **FRESH** — `tradeDate == expectedSession`.
- **STALE** — a real last-known close, older than `expectedSession`, with the gap
  counted in trading days. Served, and labelled.
- **UNAVAILABLE** — no candle on file and none obtainable. `close` is `null`.
  **Never** interpolated, never carried from a neighbouring symbol, never a zero.

`expectedSession` is the most recent NYSE session that has closed, from the
existing `MarketCalendarService`.

### 6. Fetch policy: cache-first, bounded, fail closed

1. Serve from `price_candle` when already FRESH — **no network call at all**.
2. Otherwise fan out to the quote provider, bounded by a per-request symbol cap,
   a concurrency cap, and a total deadline. (Fan-out, not batch — v7 is 401.)
3. Whatever resolves is upserted and served FRESH; whatever does not is served
   from cache as STALE, or UNAVAILABLE if there is no cache.
4. **A fetch that yields nothing leaves existing candles untouched.** This mirrors
   the FX cron rule logged 2026-08-18: an empty fetch must not overwrite good data
   with emptiness.

The batch endpoint returns HTTP 200 with per-symbol status even when some symbols
fail, because partial success is the normal case for a portfolio and the consumer
needs the good rows. The **write** path (nightly sweep) mirrors FX exactly: a
sweep that resolves nothing reports failure and changes nothing.

### 7. Nightly sweep

The daily job also refreshes `tracked_symbol` rows via the quote provider, so
last-known stays warm without a consumer having to ask. Quota-isolated from the
Alpha Vantage watchlist loop.


### 8. Bring-your-own-key (BYOK)

A caller may supply its own upstream provider credential per request:

```
X-API-Key:      <consumer key>      # who may call MarketLens        (unchanged)
X-Provider-Key: ALPHAVANTAGE=<key>  # which credential MarketLens spends upstream
```

Comma-separated for multiple providers. **MarketLens never persists and never
logs a caller-supplied key** — it is request-scoped, used, and dropped. Storage
at rest is the consumer's problem, and the hub already has the facility for it
(`secretCrypto.ts`, AES-256-GCM envelopes binding ciphertext to user + field).
MarketLens deliberately does not grow one: holding user credentials is exactly
the "personal-finance feature" its `CLAUDE.md` forbids.

*Why this matters beyond quota:* it is the honest exit from E4's accepted Yahoo
risk. A user who brings an Alpha Vantage key has their holdings priced **under
their own licence**, and Yahoo demotes from "the plan" to "the fallback for
callers who bring nothing." E4 said to revisit the unsanctioned access before any
paid tier or material growth; BYOK is what makes that revisit cheap.

#### Two different things called "API key"

The repo already contained both and named neither distinctly, which is a
readability trap:

| Concept | Header / holder | Question it answers |
|---|---|---|
| **Consumer key** | `X-API-Key`, `ApiKeyRegistry`, `AppKeyQuotaService` | May you call MarketLens? |
| **Provider key** | `X-Provider-Key`, `ProviderKeyStore` (was `ApiKeyStore`) | Whose upstream credential do we spend? |

#### Resolution chain, and what it reports

Per symbol, in order: **caller-supplied key → app-level key → keyless provider
(Yahoo/Binance) → cached last-known → UNAVAILABLE.**

Every quote reports both `source` (which provider) and `keySource`
(`USER` | `APP` | `NONE`), so a consumer can always say *"priced via Yahoo because
your Alpha Vantage quota was exhausted"* rather than silently substituting a
different licence. Falling back is allowed; falling back **quietly** is not (A6).

#### The dashboard key is a session override, deliberately

`ProviderKeyStore` is an in-memory `AtomicReference`. On Render's free plan the
service spins down on inactivity, so a key pasted into `keys.html` is gone at the
next cold start — previously a silent surprise. Rather than persist it (which
would make MarketLens a credential store without encryption at rest), the key is
now **labelled** as a session override, and a missing provider key fails closed
with a message naming `ALPHAVANTAGE_API_KEY` as the durable path.

## Consequences

- The unifier can value a dynamic holdings set, and can always tell the difference
  between *"$310.03 as of yesterday's close"*, *"$142.30 as of four sessions ago"*,
  and *"we do not know"*.
- Market data keeps exactly one owner (E3/E4). The hub calls MarketLens over HTTP
  and never re-implements a provider.
- Yahoo's accepted risks (E4) are now live: unsanctioned terms, periodic breakage.
  The registry is the mitigation — swapping providers is a config change plus one
  class, not a refactor.
- Adding a provider is: implement a capability interface, register an
  `AssetClass`, add a quota budget. No caller changes.

### Named limitations (honest, not hidden)

- **Staleness is computed against the NYSE calendar.** A TSX-listed symbol can
  read STALE by one session on a Canada-only holiday. Correct fix is a per-exchange
  calendar keyed off Yahoo's `exchangeTimezoneName`; not built.
- **No FX conversion.** MarketLens returns each price in its own currency and
  refuses to convert. Currency conversion is the hub's FX engine (`FxRate`), which
  already fails closed on a missing rate.
- **Yahoo's terms do not sanction this access** and its endpoints break
  periodically (the 2023 cookie/crumb change broke every wrapper for weeks).
  Accepted at this scale per E4; revisit before any paid tier or material growth.

## Provider strategy beyond this ADR (discussed 2026-08-18, not all built)

- **Equities, now:** Yahoo `v8/chart`. Verified working.
- **Crypto history, next:** **Binance public data** (`data.binance.vision`) —
  probed 2026-08-18, daily and monthly kline zips return **200 with no key**.
  This is *published for bulk download*, so it is a materially better risk posture
  than Yahoo. Caveat: quotes are **USDT**, not USD — the currency column carries it.
- **Crypto spot, next:** CoinGecko, through the same BYOK mechanism as §8 —
  the user's key travels per-request and is stored encrypted **in the hub**, not
  here. This resolves the original concern (per-user keys inside MarketLens would
  break its stateless, no-user-data contract) without giving up per-user quota.
- **Questrade: recommended against for the price path.** Its auth is per-user
  OAuth with a refresh token that **rotates on every use** — one missed rotation
  needs a human in the account UI, which is fragile in a cron. It requires holding
  a Questrade brokerage account, so it can never be a general provider. Its real
  value is **holdings sync** (positions/balances), which is the hub's spine, not
  MarketLens's — and whether a brokerage credential falls under A1's *"no bank
  credentials ever (no Plaid/Flinks)"* is a decision-record question that needs a
  ruling before anyone builds it.
