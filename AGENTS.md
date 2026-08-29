# MarketLens — agent router

The market-data service of a four-product ecosystem, and **its own product**. In
Unity is its first consumer, not its purpose. This repo answers "what is this
security worth and how has it behaved" — nothing wider.

**This repo must not own** personal finance: purchases, cards, budgets, or the
complete financial picture belong to In Unity (E3/A5). It must not grow a
consumer-shaped endpoint — symbols in, prices-with-currency-and-staleness out.
Both are enforced by `./mvnw verify`, not merely requested.

## One command

```
./mvnw verify
```

Compiles, runs every test including the guardrails in
`src/test/.../guardrails/`. **It is the checklist.** There is no other checklist.
Also: `./mvnw spring-boot:run`, and `./mvnw -Pdemo spring-boot:run` — demo mode
needs no PostgreSQL and no provider key, and that is a product promise.

## Read when you are…

| File | …doing this |
|---|---|
| [`quote-path.md`](docs/policies/quote-path.md) | touching quotes, staleness, the sweep, or fan-out |
| [`providers.md`](docs/policies/providers.md) | adding or changing a provider, or touching `price_candle` |
| [`api-keys.md`](docs/policies/api-keys.md) | touching auth or BYOK — consumer keys and provider keys are different things |
| [`product-surface.md`](docs/policies/product-surface.md) | adding a capability, endpoint, dashboard, or demo wiring |
| [`exceptions.json`](docs/policies/exceptions.json) | a guardrail is wrong for your task — add a dated entry and keep moving |
| [`ECOSYSTEM.md`](ECOSYSTEM.md) | anything spanning repos |
| [`FLEET.md`](FLEET.md) | recommending which model and effort to run a task at |

**Say daily or latest close, never real-time** (A6). Asserted, not requested.

## Freedom

Anything not named here and not caught by `./mvnw verify` is yours to decide.
Prefer acting and letting the build fail over asking.
Work on a branch and open a PR; it auto-merges when `verify` is green.
