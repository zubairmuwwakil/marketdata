package com.zubairmuwwakil.marketdata.model;

/**
 * Which credential paid for a given price. Reported alongside every quote so a
 * consumer can always tell a caller "your key served this" from "your key was
 * exhausted, so an unlicensed keyless source served it" — falling back is
 * allowed, falling back quietly is not (A6).
 */
public enum KeySource {
    /** The caller's own key, supplied via X-Provider-Key. */
    USER,
    /** MarketLens' app-level key for that provider. */
    APP,
    /** The provider needs no key (Yahoo, Binance public data). */
    NONE
}
