package com.zubairmuwwakil.marketdata.model;

/**
 * What kind of instrument a provider can price. Providers differ in coverage —
 * Yahoo serves equities, Binance serves crypto and no equities at all — so the
 * registry resolves by asset class rather than assuming one provider covers
 * everything.
 */
public enum AssetClass {
    EQUITY,
    CRYPTO
}
