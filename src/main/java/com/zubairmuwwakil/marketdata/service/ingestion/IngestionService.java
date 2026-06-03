package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.entity.PipelineRun;
import com.zubairmuwwakil.marketdata.model.entity.PipelineRunType;
import com.zubairmuwwakil.marketdata.model.entity.PipelineStatus;
import com.zubairmuwwakil.marketdata.repository.IngestionQuarantineRepository;
import com.zubairmuwwakil.marketdata.repository.PipelineRunRepository;
import com.zubairmuwwakil.marketdata.repository.PriceCandleUpsertRepository;
import com.zubairmuwwakil.marketdata.repository.WatchlistSymbolRepository;
import com.zubairmuwwakil.marketdata.service.indicator.IndicatorCalculationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IngestionService {

    private record ProcessOutcome(boolean success, int retriesUsed) {}

    private final MarketDataProvider marketDataProvider;
    private final WatchlistSymbolRepository watchlistRepo;
    private final QuotaService quotaService;
    private final PriceCandleUpsertRepository candleUpsertRepo;
    private final PipelineRunRepository runRepo;
    private final IndicatorCalculationService indicatorService;
    private final IngestionQuarantineRepository quarantineRepository;
    private final ObjectMapper objectMapper;

    public IngestionService(
            MarketDataProvider marketDataProvider,
            WatchlistSymbolRepository watchlistRepo,
            QuotaService quotaService,
            PriceCandleUpsertRepository candleUpsertRepo,
            PipelineRunRepository runRepo,
            IndicatorCalculationService indicatorService,
            IngestionQuarantineRepository quarantineRepository,
            ObjectMapper objectMapper
    ) {
        this.marketDataProvider = marketDataProvider;
        this.watchlistRepo = watchlistRepo;
        this.quotaService = quotaService;
        this.candleUpsertRepo = candleUpsertRepo;
        this.runRepo = runRepo;
        this.indicatorService = indicatorService;
        this.quarantineRepository = quarantineRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Daily ingestion entry point.
     * Idempotent, quota-aware, indicator-safe.
     */
    public PipelineRun ingestDaily() {
        return ingestDaily(null, "system");
    }

    public PipelineRun ingestDaily(String idempotencyKey, String startedBy) {
        Instant startedAt = Instant.now();
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDate runDate = LocalDate.now(ny);

        Optional<PipelineRun> existing = resolveIdempotency(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        var activeSymbols = watchlistRepo.findAllByActiveTrueOrderBySymbolAsc();
        int expected = activeSymbols.size();
        int remainingQuota = quotaService.remainingToday();

        int processed = 0;
        int failed = 0;
        int retriesUsed = 0;
        String lastError = null;

        LocalDate to = LocalDate.now(ny);
        LocalDate from = to.minusDays(120); // warmup for RSI/MACD

        // No quota → fail fast
        PipelineRun run = runRepo.save(PipelineRun.builder()
            .runDate(runDate)
            .status(PipelineStatus.RUNNING)
            .runType(PipelineRunType.DAILY)
            .symbolsExpected(expected)
            .symbolsProcessed(0)
            .symbolsFailed(0)
            .idempotencyKey(idempotencyKey)
            .startedBy(startedBy)
            .startedAt(startedAt)
            .build());

        if (remainingQuota == 0 && expected > 0) {
            run.setStatus(PipelineStatus.FAILED);
            run.setErrorMessage("Daily Alpha Vantage quota exhausted.");
            run.setFinishedAt(Instant.now());
            return runRepo.save(run);
        }

        List<String> symbolsToProcess = activeSymbols.stream()
                .map(s -> s.getSymbol().trim().toUpperCase(Locale.ROOT))
                .toList();

        for (String symbol : symbolsToProcess) {
            try {
                ProcessOutcome outcome = processSymbolWithRetry(symbol, from, to, run.getId());
                processed++;
                retriesUsed += outcome.retriesUsed();
            } catch (IllegalStateException quotaEx) {
                failed++;
                lastError = symbol + ": " + quotaEx.getMessage();
                break; // quota exhausted mid-run
            } catch (Exception e) {
                failed++;
                lastError = symbol + ": " + e.getMessage();
            }
        }

        PipelineStatus status;
        if (processed == 0 && failed > 0) {
            status = PipelineStatus.FAILED;
        } else if (failed > 0) {
            status = PipelineStatus.PARTIAL;
        } else {
            status = PipelineStatus.SUCCESS;
        }

        String errorMessage =
                failed == 0 ? null
                        : "Failed " + failed + "/" + expected +
                          ". Last error: " + lastError;

        run.setStatus(status);
        run.setSymbolsProcessed(processed);
        run.setSymbolsFailed(failed);
        run.setRetryCount(retriesUsed);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(Instant.now());
        return runRepo.save(run);
    }

    public PipelineRun ingestBackfill(List<String> symbols, LocalDate from, LocalDate to, String idempotencyKey, String startedBy) {
        Instant startedAt = Instant.now();
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDate runDate = LocalDate.now(ny);

        Optional<PipelineRun> existing = resolveIdempotency(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<String> symbolsToProcess = symbols == null || symbols.isEmpty()
                ? watchlistRepo.findAllByActiveTrueOrderBySymbolAsc().stream()
                .map(s -> s.getSymbol().trim().toUpperCase(Locale.ROOT))
                .toList()
                : symbols.stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .toList();

        PipelineRun run = runRepo.save(PipelineRun.builder()
                .runDate(runDate)
                .status(PipelineStatus.RUNNING)
                .runType(PipelineRunType.BACKFILL)
                .symbolsExpected(symbolsToProcess.size())
                .symbolsProcessed(0)
                .symbolsFailed(0)
                .idempotencyKey(idempotencyKey)
                .startedBy(startedBy)
                .requestedFrom(from)
                .requestedTo(to)
                .startedAt(startedAt)
                .build());

        int processed = 0;
        int failed = 0;
        int retriesUsed = 0;
        String lastError = null;

        for (String symbol : symbolsToProcess) {
            try {
                ProcessOutcome outcome = processSymbolWithRetry(symbol, from, to, run.getId());
                retriesUsed += outcome.retriesUsed();
                processed++;
            } catch (IllegalStateException quotaEx) {
                failed++;
                lastError = symbol + ": " + quotaEx.getMessage();
                break;
            } catch (Exception e) {
                failed++;
                lastError = symbol + ": " + e.getMessage();
            }
        }

        PipelineStatus status;
        if (processed == 0 && failed > 0) {
            status = PipelineStatus.FAILED;
        } else if (failed > 0) {
            status = PipelineStatus.PARTIAL;
        } else {
            status = PipelineStatus.SUCCESS;
        }

        String errorMessage = failed == 0 ? null : "Failed " + failed + "/" + symbolsToProcess.size() + ". Last error: " + lastError;

        run.setStatus(status);
        run.setSymbolsProcessed(processed);
        run.setSymbolsFailed(failed);
        run.setRetryCount(retriesUsed);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(Instant.now());
        return runRepo.save(run);
    }

    /**
     * Processes one symbol in its own transaction so a failure
     * does not roll back the entire pipeline run.
     *
     * @return true if any candles were inserted and indicators recalculated.
     */
    @Transactional
    protected ProcessOutcome processSymbolWithRetry(String symbol, LocalDate from, LocalDate to, Long runId) {
        int attempts = 0;
        RuntimeException last = null;
        while (attempts < 3) {
            try {
                processSymbol(symbol, from, to, runId);
                return new ProcessOutcome(true, attempts);
            } catch (RuntimeException ex) {
                last = ex;
                if (!isRetryableProcessException(ex) || attempts >= 2) {
                    throw ex;
                }
                attempts++;
                try {
                    Thread.sleep(250L * attempts);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (last != null) throw last;
        return new ProcessOutcome(false, attempts);
    }

    @Transactional
    protected ProcessOutcome processSymbol(String symbol, LocalDate from, LocalDate to, Long runId) {
        quotaService.consumeOneCall();

        List<DailyCandle> candles = marketDataProvider.fetchDailyCandles(symbol, from, to);
        List<DailyCandle> valid = new java.util.ArrayList<>();
        for (DailyCandle candle : candles) {
            String reason = validateCandle(candle);
            if (reason != null) {
                quarantineRepository.save(symbol, candle == null ? null : candle.tradeDate(), reason, payloadFor(candle), marketDataProvider.sourceName(), runId);
                continue;
            }
            valid.add(candle);
        }

        candleUpsertRepo.upsertAll(
            symbol,
            valid,
            false,
            marketDataProvider.sourceName()
        );

        // Always run indicators; service is idempotent and will fill gaps
        indicatorService.calculateForSymbol(symbol);
        return new ProcessOutcome(true, 0);
    }

    private Optional<PipelineRun> resolveIdempotency(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return runRepo.findByIdempotencyKey(idempotencyKey);
    }

    private String validateCandle(DailyCandle candle) {
        if (candle == null) {
            return "null_row";
        }
        if (candle.tradeDate() == null) {
            return "missing_trade_date";
        }
        if (candle.open() == null || candle.high() == null || candle.low() == null || candle.close() == null) {
            return "missing_price";
        }
        if (candle.volume() < 0) {
            return "negative_volume";
        }
        if (candle.low().compareTo(candle.high()) > 0) {
            return "invalid_price_order";
        }
        if (candle.open().signum() <= 0 || candle.high().signum() <= 0 || candle.low().signum() <= 0 || candle.close().signum() <= 0) {
            return "non_positive_price";
        }
        return null;
    }

    private String payloadFor(DailyCandle candle) {
        try {
            return objectMapper.writeValueAsString(candle);
        } catch (Exception ex) {
            return "{\"error\":\"payload_serialization_failed\"}";
        }
    }

    private boolean isRetryableProcessException(RuntimeException ex) {
        if (ex instanceof com.zubairmuwwakil.marketdata.client.ExternalServiceException serviceEx) {
            return serviceEx.isRetryable();
        }
        if (ex instanceof com.zubairmuwwakil.marketdata.resilience.CircuitBreakerOpenException) {
            return false;
        }
        if (ex instanceof IllegalStateException) {
            return false;
        }
        return true;
    }
}
