package com.zubairmuwwakil.marketdata.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IndicatorDto(
        String symbol,
        LocalDate tradeDate,
        String type,
        BigDecimal value
) {}
