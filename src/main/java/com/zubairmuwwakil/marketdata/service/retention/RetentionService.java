package com.zubairmuwwakil.marketdata.service.retention;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class RetentionService {

    private final JdbcTemplate jdbcTemplate;
    private final int priceCandleDays;

    public RetentionService(JdbcTemplate jdbcTemplate,
                            @Value("${marketdata.retention.price-candle-days:3650}") int priceCandleDays) {
        this.jdbcTemplate = jdbcTemplate;
        this.priceCandleDays = priceCandleDays;
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void purgeOldCandles() {
        LocalDate cutoff = LocalDate.now().minusDays(priceCandleDays);
        jdbcTemplate.update("DELETE FROM price_candle WHERE trade_date < ?", cutoff);
    }
}