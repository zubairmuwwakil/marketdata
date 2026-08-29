---
name: quote-path-change
description: Use when changing QuoteService, the quote sweep, fan-out, staleness, or the definition of a FRESH quote.
---

# Changing the quote path

Read [`docs/policies/quote-path.md`](../../../docs/policies/quote-path.md) first.
Its operational claims must agree with the code and tests before they are used as
implementation requirements; raise any mismatch rather than silently changing one
side.

## Invariants

- This service serves daily closes, not real-time prices.
- A current trading session is not a close. Compare fetched candles with the
  expected closed session before accepting them.
- Preserve cache and staleness semantics: a stale known price must be labelled,
  and no price must remain null rather than be fabricated.
- A scheduled job is not an external scheduler on a host that can sleep. Do not
  rely on a timer alone for a new operational guarantee without an agreed trigger.
- Treat changes to response reason/status vocabulary as consumer contract changes.

## Delivery

1. Change production code and its behavior-pinning test together; begin with
   `QuoteServiceTest` and add focused coverage when the sweep changes.
2. Keep the demo profile functional without PostgreSQL or provider credentials.
3. Run the focused test, then `./mvnw --batch-mode verify` and
   `./mvnw --batch-mode -Pdemo test`.
