package com.zubairmuwwakil.marketdata.demo;

import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.service.ingestion.MarketDataProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Profile("demo")
public class DemoMarketDataProvider implements MarketDataProvider {

    private final DemoDatasetFactory datasetFactory;

    public DemoMarketDataProvider(DemoDatasetFactory datasetFactory) {
        this.datasetFactory = datasetFactory;
    }

    @Override
    public List<DailyCandle> fetchDailyCandles(String symbol, LocalDate from, LocalDate to) {
        return datasetFactory.candlesForSymbol(symbol.toUpperCase(Locale.ROOT), from, to);
    }

    @Override
    public String sourceName() {
        return "DEMO";
    }
}
