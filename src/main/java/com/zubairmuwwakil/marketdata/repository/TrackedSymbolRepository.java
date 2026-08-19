package com.zubairmuwwakil.marketdata.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Symbols consumers have actually asked about — the dynamic counterpart to the
 * curated {@code watchlist_symbol}.
 *
 * <p>Kept separate on purpose: curated and demand symbols have different
 * lifecycles, different providers, and different quota. Holds symbols only, never
 * consumer identity or quantities.
 */
@Repository
public class TrackedSymbolRepository {

    public record TrackedSymbol(
            String symbol,
            String assetClass,
            Instant firstRequestedAt,
            Instant lastRequestedAt,
            long requestCount,
            Instant lastResolvedAt,
            String lastStatus
    ) {}

    private final JdbcTemplate jdbcTemplate;
    private final boolean h2Database;

    public TrackedSymbolRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.h2Database = DatabaseDialect.isH2(jdbcTemplate.getDataSource());
    }

    /** Registers first sighting or bumps the recency counters. Idempotent. */
    public void touch(String symbol, String assetClass) {
        String normalized = normalize(symbol);
        if (normalized.isEmpty()) return;

        String sql = h2Database ? """
                MERGE INTO tracked_symbol (symbol, asset_class, first_requested_at, last_requested_at, request_count)
                KEY (symbol)
                VALUES (?, ?,
                        COALESCE((SELECT first_requested_at FROM tracked_symbol WHERE symbol = ?), CURRENT_TIMESTAMP),
                        CURRENT_TIMESTAMP,
                        COALESCE((SELECT request_count FROM tracked_symbol WHERE symbol = ?), 0) + 1)
                """ : """
                INSERT INTO tracked_symbol (symbol, asset_class, first_requested_at, last_requested_at, request_count)
                VALUES (?, ?, NOW(), NOW(), 1)
                ON CONFLICT (symbol) DO UPDATE
                    SET last_requested_at = NOW(),
                        request_count = tracked_symbol.request_count + 1
                """;

        if (h2Database) {
            jdbcTemplate.update(sql, normalized, assetClass, normalized, normalized);
        } else {
            jdbcTemplate.update(sql, normalized, assetClass);
        }
    }

    /** Records the outcome of the last attempt to price this symbol. */
    public void recordResolution(String symbol, String status) {
        jdbcTemplate.update(
                "UPDATE tracked_symbol SET last_resolved_at = ?, last_status = ? WHERE symbol = ?",
                java.sql.Timestamp.from(Instant.now()), status, normalize(symbol));
    }

    /** Symbols to warm in the nightly sweep, most-recently-wanted first. */
    public List<TrackedSymbol> findForRefresh(int limit) {
        return jdbcTemplate.query("""
                SELECT symbol, asset_class, first_requested_at, last_requested_at,
                       request_count, last_resolved_at, last_status
                FROM tracked_symbol
                ORDER BY last_requested_at DESC
                LIMIT ?
                """, this::mapRow, Math.max(1, limit));
    }

    public List<TrackedSymbol> findAll() {
        return jdbcTemplate.query("""
                SELECT symbol, asset_class, first_requested_at, last_requested_at,
                       request_count, last_resolved_at, last_status
                FROM tracked_symbol
                ORDER BY last_requested_at DESC
                """, this::mapRow);
    }

    /** Retires symbols nobody has asked about since {@code cutoff}. */
    public int retireUnrequestedBefore(LocalDate cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM tracked_symbol WHERE last_requested_at < ?",
                java.sql.Timestamp.from(cutoff.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
    }

    private TrackedSymbol mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TrackedSymbol(
                rs.getString("symbol"),
                rs.getString("asset_class"),
                rs.getObject("first_requested_at", Instant.class),
                rs.getObject("last_requested_at", Instant.class),
                rs.getLong("request_count"),
                rs.getObject("last_resolved_at", Instant.class),
                rs.getString("last_status")
        );
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
