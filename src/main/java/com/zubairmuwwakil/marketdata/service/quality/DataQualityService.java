package com.zubairmuwwakil.marketdata.service.quality;

import com.zubairmuwwakil.marketdata.model.entity.PriceCandle;
import com.zubairmuwwakil.marketdata.repository.PriceCandleRepository;
import com.zubairmuwwakil.marketdata.service.calendar.MarketCalendarService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DataQualityService {

    public record QualityReport(
            String symbol,
            LocalDate from,
            LocalDate to,
            int expectedTradingDays,
            int storedDays,
            int missingDays,
            List<LocalDate> missingDates,
            long duplicateCount,
            long outlierCount,
            List<LocalDate> earlyCloseDates
    ) {}

    private final PriceCandleRepository candleRepository;
    private final MarketCalendarService calendarService;
    private final JdbcTemplate jdbcTemplate;

    public DataQualityService(PriceCandleRepository candleRepository,
                              MarketCalendarService calendarService,
                              JdbcTemplate jdbcTemplate) {
        this.candleRepository = candleRepository;
        this.calendarService = calendarService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public QualityReport report(String symbol, LocalDate from, LocalDate to) {
        List<LocalDate> tradingDays = calendarService.tradingDaysBetween(from, to, false);
        List<LocalDate> earlyCloses = calendarService.earlyClosesBetween(from, to);
        List<PriceCandle> candles = candleRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, from, to);
        Set<LocalDate> storedDates = new HashSet<>();
        for (PriceCandle candle : candles) {
            storedDates.add(candle.getTradeDate());
        }

        List<LocalDate> missing = tradingDays.stream()
                .filter(d -> !storedDates.contains(d))
                .toList();

        Long duplicateCount = jdbcTemplate.queryForObject(
                "select count(*) - count(distinct trade_date) from price_candle where symbol = ? and trade_date between ? and ?",
                Long.class,
                symbol,
                from,
                to
        );

        Long outlierCount = jdbcTemplate.queryForObject(
                "select count(*) from price_candle where symbol = ? and trade_date between ? and ? and (close <= 0 or volume < 0)",
                Long.class,
                symbol,
                from,
                to
        );

        return new QualityReport(
                symbol,
                from,
                to,
                tradingDays.size(),
                storedDates.size(),
                missing.size(),
                missing,
                duplicateCount == null ? 0 : duplicateCount,
                outlierCount == null ? 0 : outlierCount,
                earlyCloses
        );
    }
}
