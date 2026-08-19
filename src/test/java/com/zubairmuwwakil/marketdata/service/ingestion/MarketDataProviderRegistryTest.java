package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.config.ProviderProperties;
import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.dto.QuotedCandle;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataProviderRegistryTest {

    private record FakeCandles(String name, Set<AssetClass> classes) implements MarketDataProvider {
        @Override public List<DailyCandle> fetchDailyCandles(String s, LocalDate f, LocalDate t) { return List.of(); }
        @Override public String sourceName() { return name; }
        @Override public Set<AssetClass> supportedAssetClasses() { return classes; }
    }

    private record FakeQuotes(String name, Set<AssetClass> classes) implements LatestQuoteProvider {
        @Override public Optional<QuotedCandle> fetchLatestClose(String s) { return Optional.empty(); }
        @Override public String sourceName() { return name; }
        @Override public Set<AssetClass> supportedAssetClasses() { return classes; }
    }

    @Test
    void routesIngestionAndQuotesToDifferentProvidersByPurpose() {
        ProviderProperties props = new ProviderProperties();
        var registry = new MarketDataProviderRegistry(
                List.of(new FakeCandles("ALPHAVANTAGE", Set.of(AssetClass.EQUITY)),
                        new FakeCandles("YAHOO", Set.of(AssetClass.EQUITY))),
                List.of(new FakeQuotes("YAHOO", Set.of(AssetClass.EQUITY))),
                props);

        assertThat(registry.ingestionProvider(AssetClass.EQUITY).sourceName()).isEqualTo("ALPHAVANTAGE");
        assertThat(registry.quoteProvider(AssetClass.EQUITY).sourceName()).isEqualTo("YAHOO");
    }

    @Test
    void aSingleCoveringProviderWinsWhenTheConfiguredNameIsAbsent() {
        // This is what keeps the demo profile working: only DEMO is registered
        // there, and the configured production name simply does not exist.
        var registry = new MarketDataProviderRegistry(
                List.of(new FakeCandles("DEMO", Set.of(AssetClass.EQUITY))),
                List.of(new FakeQuotes("DEMO", Set.of(AssetClass.EQUITY))),
                new ProviderProperties());

        assertThat(registry.ingestionProvider(AssetClass.EQUITY).sourceName()).isEqualTo("DEMO");
        assertThat(registry.quoteProvider(AssetClass.EQUITY).sourceName()).isEqualTo("DEMO");
    }

    @Test
    void anAssetClassNoProviderCoversIsAnExplicitFailureNotASilentWrongAnswer() {
        var registry = new MarketDataProviderRegistry(
                List.of(new FakeCandles("YAHOO", Set.of(AssetClass.EQUITY))),
                List.of(new FakeQuotes("YAHOO", Set.of(AssetClass.EQUITY))),
                new ProviderProperties());

        assertThatThrownBy(() -> registry.quoteProvider(AssetClass.CRYPTO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRYPTO");
    }

    @Test
    void ambiguityIsRefusedRatherThanGuessed() {
        ProviderProperties props = new ProviderProperties();
        props.setQuotesEquity("NOT_REGISTERED");
        var registry = new MarketDataProviderRegistry(
                List.of(),
                List.of(new FakeQuotes("A", Set.of(AssetClass.EQUITY)),
                        new FakeQuotes("B", Set.of(AssetClass.EQUITY))),
                props);

        assertThatThrownBy(() -> registry.quoteProvider(AssetClass.EQUITY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous");
    }

    @Test
    void aConfiguredProviderThatCannotServeTheAssetClassIsNotUsed() {
        ProviderProperties props = new ProviderProperties();
        props.setQuotesEquity("CRYPTO_ONLY");
        var registry = new MarketDataProviderRegistry(
                List.of(),
                List.of(new FakeQuotes("CRYPTO_ONLY", Set.of(AssetClass.CRYPTO)),
                        new FakeQuotes("YAHOO", Set.of(AssetClass.EQUITY))),
                props);

        assertThat(registry.quoteProvider(AssetClass.EQUITY).sourceName()).isEqualTo("YAHOO");
    }
}
