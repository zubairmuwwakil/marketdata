package com.zubairmuwwakil.marketdata.security;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Upstream provider credentials supplied by the caller for the duration of one
 * request (BYOK, ADR 0003 §8).
 *
 * <p>Never persisted, never logged, never placed in MDC. Bound to the serving
 * thread by {@code ProviderKeyFilter} and cleared in a finally block — MarketLens
 * borrows the credential for one call and forgets it. Storage at rest belongs to
 * the consumer; holding user credentials here is the kind of personal-finance
 * feature this service is explicitly not allowed to grow.
 *
 * <p>Spring MVC here is thread-per-request (no WebFlux, no reactive pipeline), so
 * a ThreadLocal is the same mechanism {@code SecurityContextHolder} and MDC
 * already use in this codebase. Work handed to another thread — the quote
 * fan-out — must pass credentials explicitly rather than inheriting them.
 */
public final class ProviderCredentials {

    /** Header carrying them, e.g. {@code ALPHAVANTAGE=abc123,COINGECKO=def456}. */
    public static final String HEADER = "X-Provider-Key";

    private static final ThreadLocal<Map<String, String>> CURRENT = new ThreadLocal<>();

    private ProviderCredentials() {}

    /**
     * Parses the header value. Malformed pairs are skipped rather than rejected:
     * a caller fumbling one provider's key should still get the others served,
     * and the resolution chain already reports which key actually paid for each
     * quote, so a dropped key surfaces as {@code keySource=APP} instead of a
     * silent success.
     */
    public static Map<String, String> parse(String headerValue) {
        Map<String, String> parsed = new ConcurrentHashMap<>();
        if (headerValue == null || headerValue.isBlank()) {
            return parsed;
        }
        for (String pair : headerValue.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String provider = pair.substring(0, eq).trim().toUpperCase(Locale.ROOT);
            String key = pair.substring(eq + 1).trim();
            if (provider.isEmpty() || key.isEmpty()) continue;
            parsed.put(provider, key);
        }
        return parsed;
    }

    static void bind(Map<String, String> credentials) {
        CURRENT.set(credentials);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** The caller-supplied key for {@code providerSourceName}, if any. */
    public static Optional<String> forProvider(String providerSourceName) {
        Map<String, String> current = CURRENT.get();
        if (current == null || providerSourceName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(current.get(providerSourceName.toUpperCase(Locale.ROOT)));
    }

    /** Snapshot for handing to a worker thread, which does not inherit the binding. */
    public static Map<String, String> snapshot() {
        Map<String, String> current = CURRENT.get();
        return current == null ? Map.of() : Map.copyOf(current);
    }

    /**
     * Runs {@code work} on the current thread with {@code credentials} bound,
     * restoring whatever was bound before.
     *
     * <p>Needed because the quote path fans out across a pool: a worker thread has
     * no request binding, so a caller's key would silently degrade to the app key
     * exactly when the caller expected their own licence to be used. Symmetric
     * restore rather than a bare clear, so a pooled thread never leaks one
     * request's credential into the next.
     */
    public static <T> T callWith(Map<String, String> credentials, java.util.function.Supplier<T> work) {
        Map<String, String> previous = CURRENT.get();
        CURRENT.set(credentials == null ? Map.of() : credentials);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
