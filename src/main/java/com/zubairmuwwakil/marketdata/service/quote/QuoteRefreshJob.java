package com.zubairmuwwakil.marketdata.service.quote;

import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.QuoteBatch;
import com.zubairmuwwakil.marketdata.model.dto.QuoteStatus;
import com.zubairmuwwakil.marketdata.repository.TrackedSymbolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Keeps demand-registered symbols warm so "cache last-known" is a real guarantee
 * rather than a hopeful one.
 *
 * <p>Without this, a symbol is only ever as fresh as the last time somebody asked
 * about it, and the first consumer of the day always pays a live fetch — exactly
 * the hard dependency on a live fetch that E4 forbids. Runs after the US close,
 * in batches sized to the per-request cap.
 */
@Service
@Profile("!demo")
public class QuoteRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(QuoteRefreshJob.class);

    private final QuoteService quoteService;
    private final TrackedSymbolRepository trackedSymbolRepository;
    private final int maxSymbols;
    private final int retentionDays;

    public QuoteRefreshJob(QuoteService quoteService,
                           TrackedSymbolRepository trackedSymbolRepository,
                           @Value("${marketdata.retention.tracked-symbol-days:90}") int retentionDays,
                           @Value("${marketdata.providers.yahoo.max-symbols-per-request:50}") int maxSymbols) {
        this.quoteService = quoteService;
        this.trackedSymbolRepository = trackedSymbolRepository;
        this.retentionDays = retentionDays;
        this.maxSymbols = maxSymbols;
    }

    /** 22:30 UTC — after the US close, before the retention sweep at 02:30. */
    @Scheduled(cron = "${marketdata.providers.refresh-cron:0 30 22 * * *}")
    public void refreshTrackedSymbols() {
        List<TrackedSymbolRepository.TrackedSymbol> tracked =
                trackedSymbolRepository.findForRefresh(Integer.MAX_VALUE);
        if (tracked.isEmpty()) {
            log.info("[quote-sweep] no demand-registered symbols; nothing to warm");
            return;
        }

        int fresh = 0;
        int unavailable = 0;
        for (int start = 0; start < tracked.size(); start += maxSymbols) {
            List<String> batch = tracked.subList(start, Math.min(start + maxSymbols, tracked.size()))
                    .stream().map(TrackedSymbolRepository.TrackedSymbol::symbol).toList();
            QuoteBatch result = quoteService.quote(batch, AssetClass.EQUITY);
            for (var quote : result.quotes()) {
                if (quote.status() == QuoteStatus.FRESH) fresh++;
                if (quote.status() == QuoteStatus.UNAVAILABLE) unavailable++;
            }
        }

        // Reported honestly rather than logged as a flat success: a sweep that warms
        // nothing is a real outage signal, not a quiet no-op.
        log.info("[quote-sweep] {} symbols swept, {} fresh, {} unavailable", tracked.size(), fresh, unavailable);
        if (fresh == 0) {
            log.warn("[quote-sweep] resolved nothing — existing prices left untouched, consumers will see STALE");
        }
    }

    /** Retires symbols nobody has asked about in a long time. The demand set should
     *  reflect current interest, not accumulate forever. */
    @Scheduled(cron = "0 45 2 * * *")
    public void retireStaleSymbols() {
        int removed = trackedSymbolRepository.retireUnrequestedBefore(LocalDate.now().minusDays(retentionDays));
        if (removed > 0) {
            log.info("[quote-sweep] retired {} symbols unrequested for {} days", removed, retentionDays);
        }
    }
}
