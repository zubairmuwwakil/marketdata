package com.zubairmuwwakil.marketdata.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyCandle(
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {}