package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.config.ProviderProperties;
import com.zubairmuwwakil.marketdata.model.AssetClass;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves which provider serves a given (asset class, capability) pair.
 *
 * <p>Not optional plumbing: {@code IngestionService} used to inject a single
 * {@code MarketDataProvider} bean, so the moment Amendment E4's second provider
 * exists under the same profile the context fails to start with
 * {@code NoUniqueBeanDefinitionException}. E4 authorized a second provider without
 * noting that resolution had to come first.
 *
 * <p>Routing is by <em>purpose</em>: the curated watchlist keeps the sanctioned
 * provider, the dynamic per-user quote path uses the one with headroom (ADR 0003).
 * Adding a provider is: implement a capability interface, declare its asset
 * classes, point config at it. No caller changes.
 */
@Component
public class MarketDataProviderRegistry {

    private final Map<String, MarketDataProvider> candleProviders = new LinkedHashMap<>();
    private final Map<String, LatestQuoteProvider> quoteProviders = new LinkedHashMap<>();
    private final ProviderProperties properties;

    public MarketDataProviderRegistry(List<MarketDataProvider> candleProviders,
                                      List<LatestQuoteProvider> quoteProviders,
                                      ProviderProperties properties) {
        this.properties = properties;
        for (MarketDataProvider provider : candleProviders) {
            this.candleProviders.put(normalize(provider.sourceName()), provider);
        }
        for (LatestQuoteProvider provider : quoteProviders) {
            this.quoteProviders.put(normalize(provider.sourceName()), provider);
        }
    }

    /** Provider for curated watchlist ingestion. */
    public MarketDataProvider ingestionProvider(AssetClass assetClass) {
        return resolve(candleProviders, configuredIngestion(assetClass), assetClass,
                MarketDataProvider::supportedAssetClasses, "daily-candle");
    }

    /** Provider for the dynamic quote path. */
    public LatestQuoteProvider quoteProvider(AssetClass assetClass) {
        return resolve(quoteProviders, configuredQuotes(assetClass), assetClass,
                LatestQuoteProvider::supportedAssetClasses, "latest-quote");
    }

    public List<String> candleSources() {
        return List.copyOf(candleProviders.keySet());
    }

    public List<String> quoteSources() {
        return List.copyOf(quoteProviders.keySet());
    }

    /**
     * Configured name first; otherwise, if exactly one registered provider covers
     * the asset class, use it. The fallback is what keeps the demo profile working
     * without a config override: only the demo provider is registered there, and
     * the configured production name simply is not present.
     */
    private <P> P resolve(Map<String, P> registry,
                          String configuredName,
                          AssetClass assetClass,
                          java.util.function.Function<P, java.util.Set<AssetClass>> coverage,
                          String capability) {
        P configured = registry.get(normalize(configuredName));
        if (configured != null && coverage.apply(configured).contains(assetClass)) {
            return configured;
        }

        List<P> candidates = registry.values().stream()
                .filter(p -> coverage.apply(p).contains(assetClass))
                .toList();

        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No " + capability + " provider registered for " + assetClass
                    + ". Registered: " + registry.keySet());
        }
        throw new IllegalStateException(
                "Ambiguous " + capability + " provider for " + assetClass + ": " + registry.keySet()
                + ". Set the routing property to one of them.");
    }

    private String configuredIngestion(AssetClass assetClass) {
        return switch (assetClass) {
            case EQUITY -> properties.getIngestionEquity();
            // Crypto ingestion routing arrives with the crypto providers; until then
            // resolution falls through to whatever single provider covers it.
            case CRYPTO -> null;
        };
    }

    private String configuredQuotes(AssetClass assetClass) {
        return switch (assetClass) {
            case EQUITY -> properties.getQuotesEquity();
            case CRYPTO -> null;
        };
    }

    private String normalize(String name) {
        return Optional.ofNullable(name).orElse("").trim().toUpperCase(Locale.ROOT);
    }
}
