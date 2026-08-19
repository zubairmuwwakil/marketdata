package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.QuotedCandle;

import java.util.Optional;
import java.util.Set;

/**
 * Capability: "give me the latest daily close for this one symbol."
 *
 * <p>Deliberately separate from {@link MarketDataProvider}'s history capability.
 * The providers in play genuinely differ in what they can do — Binance has crypto
 * history but no equities, CoinGecko has spot but weak free history, Alpha
 * Vantage's free tier has history at 25 calls/day and so cannot serve a dynamic
 * set at all. Folding both capabilities into one interface would force either a
 * lowest-common-denominator contract or a leaky one; scoping them keeps providers
 * swappable and honest about coverage.
 *
 * <p>Single-symbol by design, not an oversight: Yahoo's batch quote endpoint
 * (<code>/v7/finance/quote</code>) returns HTTP 401 as of 2026-08-18, so callers
 * fan out. See ADR 0003.
 *
 * <p>This is a latest <em>daily close</em>, never a real-time quote (A6/E3).
 */
public interface LatestQuoteProvider {

    /**
     * @return the most recent daily close on file at the provider, or empty when
     *         the symbol is unknown/delisted. Implementations throw
     *         {@code ExternalServiceException} for transport-level failures so the
     *         caller can tell "no such symbol" apart from "the provider is down".
     */
    Optional<QuotedCandle> fetchLatestClose(String symbol);

    String sourceName();

    Set<AssetClass> supportedAssetClasses();
}
