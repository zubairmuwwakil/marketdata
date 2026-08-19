package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.CandleSeries;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Capability: daily OHLCV history for one symbol over a date range.
 *
 * <p>The two-method shape ({@code fetchDailyCandles} + {@code sourceName}) is the
 * one named in Amendment E4 and is kept verbatim so existing implementations are
 * unaffected. Coverage declaration is a defaulted addition, not a breaking change.
 *
 * @see LatestQuoteProvider for the spot/latest-close capability
 */
public interface MarketDataProvider {

    List<DailyCandle> fetchDailyCandles(String symbol, LocalDate from, LocalDate to);

    /** Provenance written to {@code price_candle.source}. */
    String sourceName();

    /** What this provider can price. Defaults to equities, which is what every
     *  provider predating the registry served. */
    default Set<AssetClass> supportedAssetClasses() {
        return Set.of(AssetClass.EQUITY);
    }

    /** The currency this provider reports prices in for {@code symbol}, or null
     *  when it reports none. Null means unknown — never "assume USD". */
    default String priceCurrency(String symbol) {
        return null;
    }

    /**
     * The same fetch, carrying the currency the prices are quoted in.
     *
     * <p>Defaulted so E4's two-method contract keeps working untouched. Providers
     * that learn the currency from the same response they already parsed should
     * override this rather than let a caller pay for a second round trip.
     */
    default CandleSeries fetchDailySeries(String symbol, LocalDate from, LocalDate to) {
        return new CandleSeries(symbol, priceCurrency(symbol), sourceName(),
                fetchDailyCandles(symbol, from, to));
    }
}
