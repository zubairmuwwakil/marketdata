# Two different things are called "API key"

**Read when:** touching auth, `ApiKeyRegistry`, `ProviderKeyStore`, or BYOK.

- **Consumer keys** (`X-API-Key`, `ApiKeyRegistry`) answer *"may you call MarketLens"*.
- **Provider keys** (`X-Provider-Key: PROVIDER=key`, `ProviderKeyStore`) answer
  *"whose upstream credential do we spend"*.

Do not conflate them.

## BYOK

`X-Provider-Key` is used for one request, never persisted, never logged. **Do not add
credential storage here.** MarketLens has no encryption at rest and must not become a
credential holder — the consumer stores keys encrypted (`MoneyTalks`
`src/lib/security/providerKeys.ts`).

`keySource` on every quote reports whether the caller's key (`USER`), the app key
(`APP`), or a keyless source (`NONE`) served it. The `keys.html` provider key is an
in-memory **session override** that does not survive a restart; `ALPHAVANTAGE_API_KEY`
is the durable path.
