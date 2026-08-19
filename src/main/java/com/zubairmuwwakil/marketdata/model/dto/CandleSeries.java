package com.zubairmuwwakil.marketdata.model.dto;

import java.util.List;

/**
 * A symbol's candles plus the provenance the candles themselves cannot carry.
 *
 * <p>Currency belongs to the series, not to each row: it is a property of the
 * listing. Nullable, meaning "this provider did not say" — never "assume USD".
 * Alpha Vantage's daily series reports no currency; Yahoo's chart metadata does.
 */
public record CandleSeries(
        String symbol,
        String currency,
        String source,
        List<DailyCandle> candles
) {}
