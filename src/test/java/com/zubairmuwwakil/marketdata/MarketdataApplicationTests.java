package com.zubairmuwwakil.marketdata;

import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.repository.PriceCandleRepository;
import com.zubairmuwwakil.marketdata.repository.PriceCandleUpsertRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@Testcontainers
class MarketdataApplicationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private PriceCandleUpsertRepository priceCandleUpsertRepository;

    @Autowired
    private PriceCandleRepository priceCandleRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void upsertAndQueryCandles() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        DailyCandle candle = new DailyCandle(
                date,
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                new BigDecimal("95.00"),
                new BigDecimal("105.00"),
                1_000L
        );
        priceCandleUpsertRepository.upsertAll("MSFT", List.of(candle), false, "TEST", "USD");

        var results = priceCandleRepository.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc("MSFT", date, date);
        Assertions.assertThat(results).hasSize(1);
        Assertions.assertThat(results.get(0).getClose()).isEqualByComparingTo("105.00");
    }
}
