package com.zubairmuwwakil.marketdata.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Last-known close per symbol, for the quote path's cache-first read.
 *
 * <p>One query for the whole batch rather than a findTop1 per symbol: the quote
 * path is the one place where fifty unrelated symbols arrive at once, and fifty
 * round trips before any network work is a poor way to start.
 */
@Repository
public class LatestCandleRepository {

    public record LatestCandle(
            String symbol,
            LocalDate tradeDate,
            BigDecimal close,
            String currency,
            String source
    ) {}

    private final JdbcTemplate jdbcTemplate;

    public LatestCandleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, LatestCandle> findLatestFor(List<String> symbols) {
        Map<String, LatestCandle> out = new HashMap<>();
        if (symbols == null || symbols.isEmpty()) {
            return out;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(symbols.size(), "?"));

        // Deliberately a GROUP BY join rather than Postgres' DISTINCT ON: the demo
        // profile runs H2, and the standalone demo has to work with no Postgres at all.
        String sql = """
                SELECT p.symbol, p.trade_date, p.close, p.currency, p.source
                FROM price_candle p
                JOIN (
                    SELECT symbol, MAX(trade_date) AS max_date
                    FROM price_candle
                    WHERE symbol IN (%s)
                    GROUP BY symbol
                ) latest ON latest.symbol = p.symbol AND latest.max_date = p.trade_date
                """.formatted(placeholders);

        Object[] params = symbols.stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .toArray();

        jdbcTemplate.query(sql, params, rs -> {
            out.put(rs.getString("symbol"), new LatestCandle(
                    rs.getString("symbol"),
                    rs.getObject("trade_date", LocalDate.class),
                    rs.getBigDecimal("close"),
                    rs.getString("currency"),
                    rs.getString("source")
            ));
        });
        return out;
    }
}
