package com.zubairmuwwakil.marketdata.demo;

import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.dto.QuotedCandle;
import com.zubairmuwwakil.marketdata.service.ingestion.LatestQuoteProvider;
import com.zubairmuwwakil.marketdata.service.ingestion.MarketDataProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@Profile("demo")
public class DemoMarketDataProvider implements MarketDataProvider, LatestQuoteProvider {

    private final DemoDatasetFactory datasetFactory;

    public DemoMarketDataProvider(DemoDatasetFactory datasetFactory) {
        this.datasetFactory = datasetFactory;
    }

    @Override
    public List<DailyCandle> fetchDailyCandles(String symbol, LocalDate from, LocalDate to) {
        return datasetFactory.candlesForSymbol(symbol.toUpperCase(Locale.ROOT), from, to);
    }

    /**
     * Demo mode is a product surface, not a test fixture — {@code ./mvnw -Pdemo}
     * has to show the quote path with no PostgreSQL and no provider key at all.
     * Seeded candles are synthetic USD by construction, so reporting USD here is
     * describing the fixture rather than guessing at a real listing.
     */
    @Override
    public Optional<QuotedCandle> fetchLatestClose(String symbol) {
        LocalDate to = LocalDate.now();
        List<DailyCandle> candles = fetchDailyCandles(symbol, to.minusDays(30), to);
        if (candles.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new QuotedCandle(
                symbol.toUpperCase(Locale.ROOT),
                candles.get(candles.size() - 1),
                "USD",
                sourceName()));
    }

    @Override
    public Set<AssetClass> supportedAssetClasses() {
        return Set.of(AssetClass.EQUITY, AssetClass.CRYPTO);
    }

    @Override
    public String priceCurrency(String symbol) {
        return "USD";
    }

    @Override
    public String sourceName() {
        return "DEMO";
    }
}
