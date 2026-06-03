package com.zubairmuwwakil.marketdata.service.indicator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class MacdCalculator {

    public record MacdPoint(
            BigDecimal macd,
            BigDecimal signal,
            BigDecimal histogram
    ) {}

    private MacdCalculator() {}

    public static List<MacdPoint> calculate(List<BigDecimal> closes) {
        List<BigDecimal> ema12 = EmaCalculator.calculate(closes, 12);
        List<BigDecimal> ema26 = EmaCalculator.calculate(closes, 26);

        if (ema26.isEmpty()) {
            return List.of();
        }

        List<BigDecimal> macdLine = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            if (ema12.get(i) == null || ema26.get(i) == null) {
                macdLine.add(null);
            } else {
                macdLine.add(ema12.get(i).subtract(ema26.get(i)));
            }
        }

        List<BigDecimal> signalLine =
                EmaCalculator.calculate(
                        macdLine.stream().filter(v -> v != null).toList(), 9
                );

        List<MacdPoint> result = new ArrayList<>();

        int signalOffset = macdLine.indexOf(macdLine.stream().filter(v -> v != null).findFirst().orElse(null)) + 8;

        for (int i = 0; i < macdLine.size(); i++) {
            if (macdLine.get(i) == null || i < signalOffset) {
                result.add(null);
                continue;
            }

            int signalIdx = i - signalOffset;
            if (signalIdx < 0 || signalIdx >= signalLine.size()) {
                result.add(null);
                continue;
            }

            BigDecimal signal = signalLine.get(signalIdx);
            if (signal == null) {
                result.add(null);
                continue;
            }
            BigDecimal hist = macdLine.get(i).subtract(signal);

            result.add(new MacdPoint(macdLine.get(i), signal, hist));
        }

        return result;
    }
}
