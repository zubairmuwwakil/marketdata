package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.model.KeySource;
import com.zubairmuwwakil.marketdata.security.ProviderCredentials;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The app-level credential MarketLens spends <em>upstream</em> at a data provider.
 *
 * <p>Not to be confused with {@code ApiKeyRegistry}, which answers the unrelated
 * question of who may call MarketLens. Both were called "api key" before ADR 0003;
 * the distinction is now in the type names.
 *
 * <p>Deliberately in memory only. A key pasted into {@code keys.html} is a
 * <strong>session override</strong>: this service runs on a plan that spins down
 * on inactivity, so the override is gone at the next cold start and
 * {@code ALPHAVANTAGE_API_KEY} is the durable path. Persisting it instead would
 * make MarketLens a credential store, which would need envelope encryption at
 * rest it does not have — and holding credentials is the personal-finance
 * territory this service is not allowed to grow into.
 */
@Component
public class ProviderKeyStore {

    /** An app key and the provenance of whatever key actually won resolution. */
    public record ResolvedKey(String key, KeySource source) {
        public boolean isPresent() {
            return key != null && !key.isBlank();
        }
    }

    private final AtomicReference<String> keyRef = new AtomicReference<>("");

    public void set(String key) {
        keyRef.set(key == null ? "" : key.trim());
    }

    public String get() {
        return keyRef.get();
    }

    public boolean isConfigured() {
        String key = keyRef.get();
        return key != null && !key.isBlank();
    }

    /**
     * Resolves the credential for one upstream call: the caller's own key wins,
     * then the app key, then nothing. BYOK exists so a caller's data can be
     * fetched under the caller's own licence and quota (ADR 0003 §8).
     */
    public ResolvedKey resolve(String providerSourceName) {
        return ProviderCredentials.forProvider(providerSourceName)
                .filter(k -> !k.isBlank())
                .map(k -> new ResolvedKey(k, KeySource.USER))
                .orElseGet(() -> new ResolvedKey(keyRef.get(), KeySource.APP));
    }
}
