package com.zubairmuwwakil.marketdata.service.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class EmaCalculator {

    private static final int SCALE = 8;

    private EmaCalculator() {}

    public static List<BigDecimal> calculate(List<BigDecimal> values, int period) {
        if (values.size() < period) {
            return List.of();
        }

        List<BigDecimal> ema = new ArrayList<>();

        // 1️⃣ Seed EMA with SMA(period)
        BigDecimal sma = values.subList(0, period).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), SCALE, RoundingMode.HALF_UP);

        // Pad warmup period with nulls
        for (int i = 0; i < period - 1; i++) {
            ema.add(null);
        }
        ema.add(sma);

        // 2️⃣ Wilder EMA multiplier
        BigDecimal multiplier =
                BigDecimal.valueOf(2.0 / (period + 1));

        BigDecimal prev = sma;

        // 3️⃣ EMA calculation
        for (int i = period; i < values.size(); i++) {
            prev = values.get(i)
                    .subtract(prev)
                    .multiply(multiplier)
                    .add(prev)
                    .setScale(SCALE, RoundingMode.HALF_UP);
            ema.add(prev);
        }

        return ema;
    }
}
