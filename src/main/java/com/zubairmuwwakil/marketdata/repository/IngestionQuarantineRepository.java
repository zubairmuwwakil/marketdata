package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.dto.IngestionQuarantineEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class IngestionQuarantineRepository {

    private final JdbcTemplate jdbcTemplate;

    public IngestionQuarantineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String symbol,
                     LocalDate tradeDate,
                     String reason,
                     String payload,
                     String source,
                     Long runId) {
        jdbcTemplate.update("""
                INSERT INTO ingestion_quarantine
                    (symbol, trade_date, reason, payload, source, run_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            normalizeSymbol(symbol),
            tradeDate,
            reason,
            payload == null ? "{}" : payload,
            source,
            runId
        );
    }

    public List<IngestionQuarantineEntry> list(String symbol,
                                               LocalDate from,
                                               LocalDate to,
                                               int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, symbol, trade_date, reason, payload, source, run_id, created_at
                FROM ingestion_quarantine
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (symbol != null && !symbol.isBlank()) {
            sql.append(" AND symbol = ?");
            params.add(normalizeSymbol(symbol));
        }
        if (from != null) {
            sql.append(" AND trade_date >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" AND trade_date <= ?");
            params.add(to);
        }
        int bounded = Math.min(Math.max(limit, 1), 500);
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(bounded);

        return jdbcTemplate.query(sql.toString(), params.toArray(), this::mapRow);
    }

    private IngestionQuarantineEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getLong("id");
        String symbol = rs.getString("symbol");
        LocalDate tradeDate = rs.getObject("trade_date", LocalDate.class);
        String reason = rs.getString("reason");
        String payload = rs.getString("payload");
        String source = rs.getString("source");
        Long runId = rs.getObject("run_id", Long.class);
        Instant createdAt = rs.getObject("created_at", Instant.class);
        return new IngestionQuarantineEntry(id, symbol, tradeDate, reason, payload, source, runId, createdAt);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) return null;
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
