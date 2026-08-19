package com.zubairmuwwakil.marketdata.demo;

import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.entity.CorporateActionType;
import com.zubairmuwwakil.marketdata.service.calendar.MarketCalendarService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Profile("demo")
public class DemoDatasetFactory {

    public record CorporateActionSeed(
            String symbol,
            LocalDate actionDate,
            CorporateActionType actionType,
            BigDecimal splitFactor,
            BigDecimal dividend
    ) {
    }

    public record DemoDataset(
            LocalDate from,
            LocalDate to,
            LocalDate calendarFrom,
            LocalDate calendarTo,
            LocalDate splitDate,
            LocalDate qualityGapDate,
            String featuredSymbol,
            String qualitySymbol,
            String actionSymbol,
            List<String> activeSymbols,
            List<String> inactiveSymbols,
            List<CorporateActionSeed> corporateActions,
            Map<String, List<DailyCandle>> candlesBySymbol
    ) {
    }

    private record SymbolSpec(
            BigDecimal baseAdjustedPrice,
            BigDecimal dailyDrift,
            double waveAmplitude,
            double phase,
            long baseVolume,
            BigDecimal preSplitFactor,
            LocalDate splitDate
    ) {
        BigDecimal rawFactor(LocalDate day) {
            if (splitDate != null && day.isBefore(splitDate)) {
                return preSplitFactor;
            }
            return BigDecimal.ONE;
        }
    }

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final DemoProperties properties;
    private final MarketCalendarService calendarService;

    private volatile DemoDataset dataset;

    public DemoDatasetFactory(DemoProperties properties, MarketCalendarService calendarService) {
        this.properties = properties;
        this.calendarService = calendarService;
    }

    public DemoDataset dataset() {
        DemoDataset current = dataset;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (dataset == null) {
                dataset = build();
            }
            return dataset;
        }
    }

    public List<DailyCandle> candlesForSymbol(String symbol, LocalDate from, LocalDate to) {
        String normalized = normalize(symbol);
        if (normalized.isBlank()) {
            return List.of();
        }

        DemoDataset current = dataset();

        // Only symbols this demo actually models get a series. Previously any string
        // produced a plausible-looking price, because specFor() falls through to a
        // generic spec — so asking for a symbol that does not exist returned a
        // confident number. That was invisible while the only caller iterated the
        // seeded watchlist; the quote path accepts arbitrary user input, where an
        // invented price for a typo'd ticker is exactly the fabrication this service
        // promises never to produce. Unknown symbols now resolve to UNAVAILABLE.
        if (!current.candlesBySymbol().containsKey(normalized)) {
            return List.of();
        }

        List<LocalDate> tradingDays = calendarService.tradingDaysBetween(from, to);
        if (tradingDays.isEmpty()) {
            return List.of();
        }

        SymbolSpec spec = specFor(normalized, current.splitDate());
        LocalDate qualityGapDate = normalized.equals(current.qualitySymbol()) ? current.qualityGapDate() : null;
        return buildSeries(normalized, tradingDays, spec, qualityGapDate);
    }

    private DemoDataset build() {
        LocalDate end = latestCompletedTradingDay();
        LocalDate windowStart = end.minusDays(Math.max(120, properties.getLookbackDays()));
        List<LocalDate> tradingDays = calendarService.tradingDaysBetween(windowStart, end);
        if (tradingDays.size() > 110) {
            tradingDays = new ArrayList<>(tradingDays.subList(tradingDays.size() - 110, tradingDays.size()));
        }
        if (tradingDays.isEmpty()) {
            throw new IllegalStateException("Demo dataset requires at least one trading day.");
        }

        LocalDate from = tradingDays.get(0);
        LocalDate to = tradingDays.get(tradingDays.size() - 1);
        LocalDate splitDate = tradingDays.get(Math.max(25, (tradingDays.size() * 2) / 3));
        LocalDate qualityGapDate = tradingDays.get(Math.max(15, tradingDays.size() / 3));
        LocalDate msftDividendDate = tradingDays.get(Math.max(10, tradingDays.size() / 2));
        LocalDate aaplDividendDate = tradingDays.get(Math.min(tradingDays.size() - 1, msftDividendDateIndex(tradingDays)));

        List<String> activeSymbols = normalizeSymbols(properties.getActiveSymbols());
        List<String> inactiveSymbols = normalizeSymbols(properties.getInactiveSymbols());
        String featuredSymbol = fallbackSymbol(normalize(properties.getFeaturedSymbol()), activeSymbols, "MSFT");
        String qualitySymbol = fallbackSymbol(normalize(properties.getQualitySymbol()), inactiveSymbols, "TSLA");
        String actionSymbol = fallbackSymbol(normalize(properties.getActionSymbol()), activeSymbols, "NVDA");

        Set<String> symbols = new LinkedHashSet<>();
        symbols.addAll(activeSymbols);
        symbols.addAll(inactiveSymbols);
        symbols.add(featuredSymbol);
        symbols.add(qualitySymbol);
        symbols.add(actionSymbol);

        Map<String, List<DailyCandle>> candlesBySymbol = new LinkedHashMap<>();
        for (String symbol : symbols) {
            LocalDate gapDate = symbol.equals(qualitySymbol) ? qualityGapDate : null;
            candlesBySymbol.put(symbol, buildSeries(symbol, tradingDays, specFor(symbol, splitDate), gapDate));
        }

        List<CorporateActionSeed> corporateActions = List.of(
                new CorporateActionSeed(actionSymbol, splitDate, CorporateActionType.SPLIT, new BigDecimal("2.000000"), null),
                new CorporateActionSeed("MSFT", msftDividendDate, CorporateActionType.DIVIDEND, null, new BigDecimal("0.83")),
                new CorporateActionSeed("AAPL", aaplDividendDate, CorporateActionType.DIVIDEND, null, new BigDecimal("0.29"))
        );

        YearMonth currentMonth = YearMonth.from(to);
        return new DemoDataset(
                from,
                to,
                currentMonth.atDay(1),
                currentMonth.atEndOfMonth(),
                splitDate,
                qualityGapDate,
                featuredSymbol,
                qualitySymbol,
                actionSymbol,
                activeSymbols,
                inactiveSymbols,
                corporateActions,
                candlesBySymbol
        );
    }

    private int msftDividendDateIndex(List<LocalDate> tradingDays) {
        int midpoint = tradingDays.size() / 2;
        return Math.min(tradingDays.size() - 1, midpoint + 12);
    }

    private LocalDate latestCompletedTradingDay() {
        LocalDate cursor = LocalDate.now(NEW_YORK).minusDays(1);
        while (!calendarService.isTradingDay(cursor)) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }

    private List<String> normalizeSymbols(List<String> values) {
        List<String> normalized = new ArrayList<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            String symbol = normalize(value);
            if (!symbol.isBlank() && !normalized.contains(symbol)) {
                normalized.add(symbol);
            }
        }
        return normalized;
    }

    private String fallbackSymbol(String requested, List<String> candidates, String fallback) {
        if (!requested.isBlank()) {
            return requested;
        }
        if (!candidates.isEmpty()) {
            return candidates.get(0);
        }
        return fallback;
    }

    private SymbolSpec specFor(String symbol, LocalDate splitDate) {
        return switch (symbol) {
            case "MSFT" -> new SymbolSpec(
                    new BigDecimal("421.00"),
                    new BigDecimal("0.0009"),
                    0.010,
                    1.1,
                    20_500_000L,
                    BigDecimal.ONE,
                    null
            );
            case "AAPL" -> new SymbolSpec(
                    new BigDecimal("214.00"),
                    new BigDecimal("0.0006"),
                    0.011,
                    2.4,
                    24_700_000L,
                    BigDecimal.ONE,
                    null
            );
            case "NVDA" -> new SymbolSpec(
                    new BigDecimal("132.00"),
                    new BigDecimal("0.0011"),
                    0.018,
                    0.6,
                    38_500_000L,
                    new BigDecimal("2.000000"),
                    splitDate
            );
            case "SPY" -> new SymbolSpec(
                    new BigDecimal("609.00"),
                    new BigDecimal("0.0004"),
                    0.006,
                    3.7,
                    9_100_000L,
                    BigDecimal.ONE,
                    null
            );
            case "TSLA" -> new SymbolSpec(
                    new BigDecimal("182.00"),
                    new BigDecimal("-0.0002"),
                    0.024,
                    1.9,
                    41_200_000L,
                    BigDecimal.ONE,
                    null
            );
            default -> genericSpec(symbol);
        };
    }

    private SymbolSpec genericSpec(String symbol) {
        int hash = Math.abs(symbol.hashCode());
        BigDecimal baseAdjusted = BigDecimal.valueOf(45 + (hash % 240L));
        BigDecimal drift = BigDecimal.valueOf(((hash % 15) - 4L) / 10_000.0);
        double amplitude = 0.007 + ((hash % 9) * 0.0015);
        double phase = (hash % 13) / 3.0;
        long volume = 900_000L + ((long) (hash % 40) * 180_000L);
        return new SymbolSpec(baseAdjusted, drift, amplitude, phase, volume, BigDecimal.ONE, null);
    }

    private List<DailyCandle> buildSeries(String symbol,
                                          List<LocalDate> tradingDays,
                                          SymbolSpec spec,
                                          LocalDate qualityGapDate) {
        List<DailyCandle> candles = new ArrayList<>();
        BigDecimal previousClose = null;

        for (int i = 0; i < tradingDays.size(); i++) {
            LocalDate day = tradingDays.get(i);
            if (qualityGapDate != null && day.equals(qualityGapDate)) {
                continue;
            }

            BigDecimal adjustedClose = adjustedClose(spec, i);
            BigDecimal close = scalePrice(adjustedClose.multiply(spec.rawFactor(day)));

            BigDecimal referencePreviousClose = previousClose == null ? close : previousClose;
            if (spec.splitDate() != null && day.equals(spec.splitDate())) {
                referencePreviousClose = scalePrice(referencePreviousClose.divide(spec.preSplitFactor(), 6, RoundingMode.HALF_UP));
            }

            BigDecimal open = scalePrice(referencePreviousClose.multiply(BigDecimal.valueOf(1 + Math.sin(i + spec.phase()) * 0.0035)));
            BigDecimal range = close.multiply(BigDecimal.valueOf(0.012 + Math.abs(Math.cos((i + spec.phase()) / 4.0)) * 0.016));
            BigDecimal high = scalePrice(open.max(close).add(range));
            BigDecimal low = scalePrice(open.min(close).subtract(range).max(new BigDecimal("1.00")));
            long volume = Math.max(
                    750_000L,
                    Math.round(spec.baseVolume() * (0.88 + Math.abs(Math.cos((i + spec.phase()) / 7.0)) * 0.42))
            );

            candles.add(new DailyCandle(day, open, high, low, close, volume));
            previousClose = close;
        }

        return candles;
    }

    private BigDecimal adjustedClose(SymbolSpec spec, int dayIndex) {
        double trendMultiplier = 1 + (dayIndex * spec.dailyDrift().doubleValue());
        double seasonalMultiplier = 1
                + (Math.sin((dayIndex + spec.phase()) / 6.0) * spec.waveAmplitude())
                + (Math.cos((dayIndex + spec.phase()) / 13.0) * (spec.waveAmplitude() / 2.0));
        BigDecimal close = spec.baseAdjustedPrice().multiply(BigDecimal.valueOf(trendMultiplier * seasonalMultiplier));
        return close.max(new BigDecimal("8.00"));
    }

    private BigDecimal scalePrice(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
