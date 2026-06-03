package com.zubairmuwwakil.marketdata.model.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class PriceCandleId implements Serializable {

    private String symbol;
    private LocalDate tradeDate;

    public PriceCandleId() {}

    public PriceCandleId(String symbol, LocalDate tradeDate) {
        this.symbol = symbol;
        this.tradeDate = tradeDate;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PriceCandleId that = (PriceCandleId) o;
        return Objects.equals(symbol, that.symbol) && Objects.equals(tradeDate, that.tradeDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, tradeDate);
    }
}
