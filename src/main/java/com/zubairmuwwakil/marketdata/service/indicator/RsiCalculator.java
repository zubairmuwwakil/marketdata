package com.zubairmuwwakil.marketdata.service.indicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class RsiCalculator {

    private static final int PERIOD = 14;
    private static final BigDecimal PERIOD_BD = BigDecimal.valueOf(PERIOD);
    private static final BigDecimal SMOOTHING = BigDecimal.valueOf(PERIOD - 1);

    public static List<BigDecimal> calculate(List<BigDecimal> closes) {
        if (closes.size() <= PERIOD) return List.of();

        List<BigDecimal> result = new ArrayList<>();

        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;

        // Initial averages
        for (int i = 1; i <= PERIOD; i++) {
            BigDecimal diff = closes.get(i).subtract(closes.get(i - 1));
            if (diff.signum() > 0) gain = gain.add(diff);
            else loss = loss.add(diff.abs());
        }

        BigDecimal avgGain = gain.divide(PERIOD_BD, 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = loss.divide(PERIOD_BD, 8, RoundingMode.HALF_UP);

        result.add(rsi(avgGain, avgLoss));

        for (int i = PERIOD + 1; i < closes.size(); i++) {
            BigDecimal diff = closes.get(i).subtract(closes.get(i - 1));
            BigDecimal g = diff.signum() > 0 ? diff : BigDecimal.ZERO;
            BigDecimal l = diff.signum() < 0 ? diff.abs() : BigDecimal.ZERO;

            avgGain = avgGain.multiply(SMOOTHING)
                    .add(g)
                    .divide(PERIOD_BD, 8, RoundingMode.HALF_UP);

            avgLoss = avgLoss.multiply(SMOOTHING)
                    .add(l)
                    .divide(PERIOD_BD, 8, RoundingMode.HALF_UP);

            result.add(rsi(avgGain, avgLoss));
        }

        return result;
    }

    private static BigDecimal rsi(BigDecimal gain, BigDecimal loss) {
        if (loss.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.valueOf(100);
        BigDecimal rs = gain.divide(loss, 8, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100)
                        .divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));
    }
}
