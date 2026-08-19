package com.zubairmuwwakil.marketdata.model.dto;

/**
 * One candle plus the provenance a bare {@link DailyCandle} cannot carry: the
 * currency the price is quoted in, and which provider said so.
 *
 * <p>Currency is nullable and means "the provider did not report one" — never
 * "assume USD". A price without a currency cannot be summed with another price,
 * so consumers must fail closed on a null rather than guess. Alpha Vantage's
 * daily series reports no currency; Yahoo's chart metadata does.
 */
public record QuotedCandle(
        String symbol,
        DailyCandle candle,
        String currency,
        String source
) {}
