package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;

import java.time.LocalDate;
import java.util.List;

public interface MarketDataProvider {
    List<DailyCandle> fetchDailyCandles(String symbol, LocalDate from, LocalDate to);
    String sourceName();
}