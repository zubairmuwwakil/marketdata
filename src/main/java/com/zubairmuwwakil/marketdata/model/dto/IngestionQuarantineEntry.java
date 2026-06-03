package com.zubairmuwwakil.marketdata.model.dto;

import java.time.Instant;
import java.time.LocalDate;

public record IngestionQuarantineEntry(
        Long id,
        String symbol,
        LocalDate tradeDate,
        String reason,
        String payload,
        String source,
        Long runId,
        Instant createdAt
) {}
