package com.zubairmuwwakil.marketdata.model.dto;

import com.zubairmuwwakil.marketdata.model.KeySource;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One symbol's latest daily close, with everything a consumer needs to decide
 * whether it may use the number.
 *
 * <p>The status field is the honesty mechanism: a portfolio valuation must be able
 * to tell "$310.03 as of yesterday's close" from "$142.30 as of four sessions ago"
 * from "we do not know", and must never receive a fabricated stand-in for the
 * third case.
 */
public record SymbolQuote(
        String symbol,
        QuoteStatus status,
        /** Null whenever status is UNAVAILABLE. Never interpolated, never zero-filled. */
        BigDecimal close,
        /** ISO-4217, or null when no provider reported one. Null means the consumer
         *  must not add this figure to any other figure. */
        String currency,
        LocalDate tradeDate,
        /** Which provider supplied it — provenance rides sourceName() per E4. */
        String source,
        /** Whose credential paid for it. */
        KeySource keySource,
        /** Sessions between this price and the last expected close. 0 when FRESH. */
        Integer staleTradingDays,
        /** Machine-readable cause when UNAVAILABLE; null otherwise. */
        String reason
) {
    public static SymbolQuote unavailable(String symbol, String reason) {
        return new SymbolQuote(symbol, QuoteStatus.UNAVAILABLE, null, null, null, null, null, null, reason);
    }
}
