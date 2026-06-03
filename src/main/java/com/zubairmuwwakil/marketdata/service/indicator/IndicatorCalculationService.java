package com.zubairmuwwakil.marketdata.service.indicator;

import com.zubairmuwwakil.marketdata.model.entity.*;
import com.zubairmuwwakil.marketdata.repository.PriceCandleRepository;
import com.zubairmuwwakil.marketdata.repository.TechnicalIndicatorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class IndicatorCalculationService {

    private final PriceCandleRepository candleRepo;
    private final TechnicalIndicatorRepository indicatorRepo;

    public IndicatorCalculationService(
            PriceCandleRepository candleRepo,
            TechnicalIndicatorRepository indicatorRepo
    ) {
        this.candleRepo = candleRepo;
        this.indicatorRepo = indicatorRepo;
    }

    /**
     * Entry point — calculate all indicators for one symbol.
     * Safe to call repeatedly (idempotent).
     */
    @Transactional
    public void calculateForSymbol(String symbol) {
        List<PriceCandle> candles =
                candleRepo.findBySymbolOrderByTradeDateAsc(symbol)
                        .stream()
                        .filter(c -> c.getClose() != null)
                        .toList();

        if (candles.size() < 30) {
            return; // not enough data for MACD
        }

        calculateRsi(symbol, candles);
        calculateMacd(symbol, candles);
    }

    /* =========================
       RSI (14)
       ========================= */

    private void calculateRsi(String symbol, List<PriceCandle> candles) {
        int period = 14;

        for (int i = period; i < candles.size(); i++) {
            LocalDate date = candles.get(i).getTradeDate();

            if (indicatorRepo.existsBySymbolAndTradeDateAndIndicatorType(
                    symbol, date, IndicatorType.RSI_14)) {
                continue;
            }

            BigDecimal gain = BigDecimal.ZERO;
            BigDecimal loss = BigDecimal.ZERO;

            for (int j = i - period + 1; j <= i; j++) {
                BigDecimal diff = candles.get(j).getClose()
                        .subtract(candles.get(j - 1).getClose());

                if (diff.signum() > 0) {
                    gain = gain.add(diff);
                } else {
                    loss = loss.add(diff.abs());
                }
            }

            if (loss.compareTo(BigDecimal.ZERO) == 0) {
                continue; // avoid divide-by-zero
            }

            BigDecimal rs = gain.divide(loss, 8, RoundingMode.HALF_UP);
            BigDecimal rsi = BigDecimal.valueOf(100)
                    .subtract(
                            BigDecimal.valueOf(100)
                                    .divide(rs.add(BigDecimal.ONE), 8, RoundingMode.HALF_UP)
                    );

            saveIndicator(symbol, date, IndicatorType.RSI_14, rsi);
        }
    }

    /* =========================
       MACD (12, 26, 9)
       ========================= */

    private void saveIfMissing(
        String symbol,
        LocalDate date,
        IndicatorType type,
        BigDecimal value
) {
    if (!indicatorRepo.existsBySymbolAndTradeDateAndIndicatorType(symbol, date, type)) {
        indicatorRepo.save(
                TechnicalIndicator.builder()
                        .symbol(symbol)
                        .tradeDate(date)
                        .indicatorType(type)
                        .value(value)
                        .build()
        );
    }
}


    private void calculateMacd(String symbol, List<PriceCandle> candles) {

    List<BigDecimal> closes =
            candles.stream().map(PriceCandle::getClose).toList();

    List<MacdCalculator.MacdPoint> macd =
            MacdCalculator.calculate(closes);

    for (int i = 0; i < macd.size(); i++) {
        MacdCalculator.MacdPoint p = macd.get(i);
        if (p == null) continue;

        LocalDate date = candles.get(i).getTradeDate();

        saveIfMissing(symbol, date, IndicatorType.MACD, p.macd());
        saveIfMissing(symbol, date, IndicatorType.MACD_SIGNAL, p.signal());
        saveIfMissing(symbol, date, IndicatorType.MACD_HISTOGRAM, p.histogram());
    }
}


    /* =========================
       EMA helper
       ========================= */

    @SuppressWarnings("unused")
    private List<BigDecimal> ema(List<BigDecimal> values, int period) {
        List<BigDecimal> ema = new ArrayList<>();
        BigDecimal multiplier =
                BigDecimal.valueOf(2.0 / (period + 1));

        BigDecimal prev = values.get(0);
        ema.add(prev);

        for (int i = 1; i < values.size(); i++) {
            prev = values.get(i)
                    .subtract(prev)
                    .multiply(multiplier)
                    .add(prev)
                    .setScale(8, RoundingMode.HALF_UP);
            ema.add(prev);
        }

        return ema;
    }

    /* =========================
       Persist helper
       ========================= */

    private void saveIndicator(
            String symbol,
            LocalDate date,
            IndicatorType type,
            BigDecimal value
    ) {
        TechnicalIndicator indicator = TechnicalIndicator.builder()
                .symbol(symbol)
                .tradeDate(date)
                .indicatorType(type)
                .value(value)
                .build();

        indicatorRepo.save(indicator);
    }
}
