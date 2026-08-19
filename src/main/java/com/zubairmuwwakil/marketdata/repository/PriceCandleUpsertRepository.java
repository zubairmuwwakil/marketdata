package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class PriceCandleUpsertRepository {

    private final JdbcTemplate jdbcTemplate;
    private final boolean h2Database;

    public PriceCandleUpsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.h2Database = detectH2(jdbcTemplate.getDataSource());
    }

    /**
     * @param currency ISO-4217 the provider quoted these prices in, or null when it
     *                 reported none. Stored as given — a guessed currency is worse
     *                 than an absent one, because an absent one fails closed.
     */
    public int upsertAll(String symbol, List<DailyCandle> candles, boolean adjusted, String source, String currency) {
        if (candles == null || candles.isEmpty()) {
            return 0;
        }
        String sql = h2Database ? """
                MERGE INTO price_candle
                    (symbol, trade_date, open, high, low, close, volume, adjusted, source, created_at, currency)
                KEY (symbol, trade_date)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
                : """
                INSERT INTO price_candle
                    (symbol, trade_date, open, high, low, close, volume, adjusted, source, created_at, currency)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (symbol, trade_date) DO UPDATE
                    SET open = EXCLUDED.open,
                        high = EXCLUDED.high,
                        low = EXCLUDED.low,
                        close = EXCLUDED.close,
                        volume = EXCLUDED.volume,
                        adjusted = EXCLUDED.adjusted,
                        source = EXCLUDED.source,
                        -- Never let a provider that reports no currency erase one a
                        -- better-informed provider already established.
                        currency = COALESCE(EXCLUDED.currency, price_candle.currency)
                """;

        // TIMESTAMPTZ column: pgjdbc cannot infer a SQL type for a bare Instant,
        // so bind an OffsetDateTime. Same instant, a type the driver can map.
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

        int[][] counts = jdbcTemplate.batchUpdate(sql, candles, candles.size(), (ps, c) -> {
            ps.setString(1, symbol);
            ps.setObject(2, c.tradeDate());
            ps.setBigDecimal(3, c.open());
            ps.setBigDecimal(4, c.high());
            ps.setBigDecimal(5, c.low());
            ps.setBigDecimal(6, c.close());
            ps.setLong(7, c.volume());
            ps.setBoolean(8, adjusted);
            ps.setString(9, source);
            ps.setObject(10, createdAt);
            ps.setString(11, currency);
        });
        int total = 0;
        for (int[] batch : counts) {
            for (int c : batch) {
                total += c;
            }
        }
        return total;
    }

    private boolean detectH2(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("h2");
        } catch (SQLException ignored) {
            return false;
        }
    }
}
