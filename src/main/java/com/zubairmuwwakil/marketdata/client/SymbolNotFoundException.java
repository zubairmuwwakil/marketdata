package com.zubairmuwwakil.marketdata.client;

/**
 * The provider has no such symbol — a typo, a delisting, or an instrument it does
 * not cover. Permanent: retrying spends budget to learn the same thing, and the
 * caller should report the symbol as unavailable rather than as an outage.
 */
public class SymbolNotFoundException extends RuntimeException {

    private final String symbol;

    public SymbolNotFoundException(String symbol) {
        super("Symbol not found at provider: " + symbol);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
