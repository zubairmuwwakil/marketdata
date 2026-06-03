package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.entity.IndicatorType;
import com.zubairmuwwakil.marketdata.model.entity.TechnicalIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TechnicalIndicatorRepository
        extends JpaRepository<TechnicalIndicator, Long> {

    // Idempotency check (critical)
    boolean existsBySymbolAndTradeDateAndIndicatorType(
            String symbol,
            LocalDate tradeDate,
            IndicatorType indicatorType
    );

    // Fetch a single indicator value (charts, debugging)
    Optional<TechnicalIndicator> findBySymbolAndTradeDateAndIndicatorType(
            String symbol,
            LocalDate tradeDate,
            IndicatorType indicatorType
    );

    // Fetch full time series for charts
    List<TechnicalIndicator> findAllBySymbolAndIndicatorTypeOrderByTradeDateAsc(
            String symbol,
            IndicatorType indicatorType
    );

    // Bulk fetch for a symbol/day (useful later)
    List<TechnicalIndicator> findAllBySymbolAndTradeDate(
            String symbol,
            LocalDate tradeDate
    );
    List<TechnicalIndicator> findAllBySymbolOrderByTradeDateAsc(
            String symbol
    );

    List<TechnicalIndicator> findAllBySymbolAndTradeDateGreaterThanOrderByTradeDateAsc(
            String symbol,
            LocalDate tradeDate,
            Pageable pageable
    );

    List<TechnicalIndicator> findAllBySymbolAndIndicatorTypeAndTradeDateGreaterThanOrderByTradeDateAsc(
            String symbol,
            IndicatorType indicatorType,
            LocalDate tradeDate,
            Pageable pageable
    );
}
