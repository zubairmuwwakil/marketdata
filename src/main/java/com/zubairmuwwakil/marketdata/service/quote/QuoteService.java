package com.zubairmuwwakil.marketdata.service.quote;

import com.zubairmuwwakil.marketdata.config.ProviderProperties;
import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.KeySource;
import com.zubairmuwwakil.marketdata.model.dto.*;
import com.zubairmuwwakil.marketdata.repository.LatestCandleRepository;
import com.zubairmuwwakil.marketdata.repository.PriceCandleUpsertRepository;
import com.zubairmuwwakil.marketdata.repository.TrackedSymbolRepository;
import com.zubairmuwwakil.marketdata.security.ProviderCredentials;
import com.zubairmuwwakil.marketdata.service.calendar.MarketCalendarService;
import com.zubairmuwwakil.marketdata.service.ingestion.LatestQuoteProvider;
import com.zubairmuwwakil.marketdata.service.ingestion.MarketDataProviderRegistry;
import com.zubairmuwwakil.marketdata.service.ingestion.QuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/**
 * Latest daily closes for an arbitrary, caller-supplied symbol set.
 *
 * <p>The gap this fills: ingestion serves a fixed watchlist on a schedule, while a
 * consumer's holdings are dynamic. This is the read path for the dynamic set, and
 * it obeys one invariant above all others — <strong>valuation never hard-depends
 * on a live fetch</strong>. Cache last-known, label staleness, fail closed rather
 * than fabricate a number (Amendment E4, mirroring the FX cron rule where an empty
 * fetch leaves existing rates untouched).
 *
 * <p>Daily closes, never real-time (A6).
 */
@Service
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime EARLY_CLOSE = LocalTime.of(13, 0);

    private final MarketDataProviderRegistry providerRegistry;
    private final LatestCandleRepository latestCandleRepository;
    private final PriceCandleUpsertRepository candleUpsertRepository;
    private final TrackedSymbolRepository trackedSymbolRepository;
    private final MarketCalendarService calendarService;
    private final QuotaService quotaService;
    private final ProviderProperties providerProperties;
    private final Clock clock;

    public QuoteService(MarketDataProviderRegistry providerRegistry,
                        LatestCandleRepository latestCandleRepository,
                        PriceCandleUpsertRepository candleUpsertRepository,
                        TrackedSymbolRepository trackedSymbolRepository,
                        MarketCalendarService calendarService,
                        QuotaService quotaService,
                        ProviderProperties providerProperties,
                        Clock clock) {
        this.providerRegistry = providerRegistry;
        this.latestCandleRepository = latestCandleRepository;
        this.candleUpsertRepository = candleUpsertRepository;
        this.trackedSymbolRepository = trackedSymbolRepository;
        this.calendarService = calendarService;
        this.quotaService = quotaService;
        this.providerProperties = providerProperties;
        this.clock = clock;
    }

    public QuoteBatch quote(List<String> requestedSymbols, AssetClass assetClass) {
        int cap = assetClass == AssetClass.CRYPTO
                ? providerProperties.getBinance().getMaxSymbolsPerRequest()
                : providerProperties.getYahoo().getMaxSymbolsPerRequest();

        List<String> normalized = requestedSymbols == null ? List.of() : requestedSymbols.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        // Truncation is reported, never silent: a consumer that quietly loses the
        // tail of its portfolio would show a confidently wrong total.
        List<String> symbols = normalized.size() > cap ? normalized.subList(0, cap) : normalized;
        List<String> truncated = normalized.size() > cap
                ? List.copyOf(normalized.subList(cap, normalized.size()))
                : List.of();

        LocalDate expectedSession = expectedSessionFor(assetClass);
        if (symbols.isEmpty()) {
            return new QuoteBatch("daily-close", expectedSession, List.of(), truncated);
        }

        for (String symbol : symbols) {
            trackedSymbolRepository.touch(symbol, assetClass.name());
        }

        Map<String, LatestCandleRepository.LatestCandle> cached =
                latestCandleRepository.findLatestFor(symbols);

        List<String> needRefresh = symbols.stream()
                .filter(symbol -> {
                    var hit = cached.get(symbol);
                    return hit == null || hit.tradeDate() == null || hit.tradeDate().isBefore(expectedSession);
                })
                .toList();

        Map<String, QuotedCandle> fetched = needRefresh.isEmpty()
                ? Map.of()
                : refresh(needRefresh, assetClass);

        List<SymbolQuote> quotes = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            SymbolQuote quote = buildQuote(symbol, expectedSession, cached.get(symbol), fetched.get(symbol), assetClass);
            trackedSymbolRepository.recordResolution(symbol, quote.status().name());
            quotes.add(quote);
        }

        return new QuoteBatch("daily-close", expectedSession, List.copyOf(quotes), truncated);
    }

    /**
     * Fans out to the provider for symbols the cache cannot answer freshly.
     */
    private Map<String, QuotedCandle> refresh(List<String> symbols, AssetClass assetClass) {
        LatestQuoteProvider defaultProvider;
        try {
            defaultProvider = providerRegistry.quoteProvider(assetClass);
        } catch (IllegalStateException ex) {
            log.warn("[quotes] no quote provider for {}: {}", assetClass, ex.getMessage());
            return Map.of();
        }

        boolean enabled;
        int maxConcurrency;
        int dailyBudget;
        Duration fanOutDeadline;

        if (assetClass == AssetClass.CRYPTO) {
            ProviderProperties.Binance binance = providerProperties.getBinance();
            enabled = binance.isEnabled();
            maxConcurrency = binance.getMaxConcurrency();
            dailyBudget = binance.getDailyBudget();
            fanOutDeadline = binance.getFanOutDeadline();
        } else {
            ProviderProperties.Yahoo yahoo = providerProperties.getYahoo();
            enabled = yahoo.isEnabled();
            maxConcurrency = yahoo.getMaxConcurrency();
            dailyBudget = yahoo.getDailyBudget();
            fanOutDeadline = yahoo.getFanOutDeadline();
        }

        if (!enabled) {
            return Map.of();
        }

        Map<String, QuotedCandle> resolved = new ConcurrentHashMap<>();
        Semaphore concurrency = new Semaphore(Math.max(1, maxConcurrency));
        // Worker threads do not inherit the request's ThreadLocal binding, so a
        // caller's own key would silently degrade to the app key without this.
        Map<String, String> credentials = ProviderCredentials.snapshot();

        // Check if caller supplied credentials for a specific provider supporting this asset class
        List<LatestQuoteProvider> userProviders = credentials.keySet().stream()
                .map(source -> providerRegistry.findQuoteProvider(source, assetClass))
                .flatMap(Optional::stream)
                .toList();

        List<Callable<Void>> tasks = symbols.stream()
                .map(symbol -> (Callable<Void>) () -> {
                    concurrency.acquire();
                    try {
                        ProviderCredentials.callWith(credentials, () -> {
                            // 1. Try user-keyed providers first if caller provided a key
                            for (LatestQuoteProvider userProvider : userProviders) {
                                try {
                                    Optional<QuotedCandle> quoted = userProvider.fetchLatestClose(symbol);
                                    if (quoted.isPresent()) {
                                        resolved.put(symbol, quoted.get());
                                        return null;
                                    }
                                } catch (RuntimeException ex) {
                                    log.warn("[quotes] user-keyed {} fetch failed for {}: {}",
                                            userProvider.sourceName(), symbol, ex.toString());
                                }
                            }

                            // 2. Fall back to default quote provider
                            try {
                                boolean hasUserKeyForDefault = credentials.containsKey(defaultProvider.sourceName());
                                if (!hasUserKeyForDefault && !quotaService.tryConsumeOneCall(defaultProvider.sourceName(), dailyBudget)) {
                                    log.info("[quotes] budget exhausted for {}; serving cached last-known", defaultProvider.sourceName());
                                    return null;
                                }
                                defaultProvider.fetchLatestClose(symbol).ifPresent(q -> resolved.put(symbol, q));
                            } catch (RuntimeException ex) {
                                log.warn("[quotes] {} refresh failed for {}: {}", defaultProvider.sourceName(), symbol, ex.toString());
                            }
                            return null;
                        });
                    } finally {
                        concurrency.release();
                    }
                    return null;
                })
                .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.invokeAll(tasks, fanOutDeadline.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        persist(resolved);
        return resolved;
    }

    /**
     * Writes what we learned. A refresh that resolved nothing writes nothing and
     * leaves every existing candle untouched — the same rule as the FX cron, where
     * an empty fetch must never overwrite good data with emptiness.
     */
    private void persist(Map<String, QuotedCandle> resolved) {
        for (QuotedCandle quoted : resolved.values()) {
            try {
                candleUpsertRepository.upsertAll(
                        quoted.symbol(),
                        List.of(quoted.candle()),
                        false,
                        quoted.source(),
                        quoted.currency());
            } catch (RuntimeException ex) {
                log.warn("[quotes] failed to persist {}: {}", quoted.symbol(), ex.toString());
            }
        }
    }

    private SymbolQuote buildQuote(String symbol,
                                   LocalDate expectedSession,
                                   LatestCandleRepository.LatestCandle cached,
                                   QuotedCandle fetched,
                                   AssetClass assetClass) {
        if (fetched != null) {
            LocalDate tradeDate = fetched.candle().tradeDate();
            return new SymbolQuote(
                    symbol,
                    statusFor(tradeDate, expectedSession),
                    fetched.candle().close(),
                    fetched.currency(),
                    tradeDate,
                    fetched.source(),
                    keySourceFor(fetched.source()),
                    staleDays(tradeDate, expectedSession, assetClass),
                    null);
        }

        if (cached != null && cached.close() != null && cached.tradeDate() != null) {
            return new SymbolQuote(
                    symbol,
                    statusFor(cached.tradeDate(), expectedSession),
                    cached.close(),
                    cached.currency(),
                    cached.tradeDate(),
                    cached.source(),
                    keySourceFor(cached.source()),
                    staleDays(cached.tradeDate(), expectedSession, assetClass),
                    null);
        }

        // Nothing cached and nothing fetched. Fail closed: no interpolation, no
        // carry-forward from another symbol, no zero.
        return SymbolQuote.unavailable(symbol, "no_data");
    }

    /**
     * Whose credential paid. Keyless providers are reported as {@code NONE} rather
     * than left blank, so a consumer can say "priced via an unlicensed source
     * because you brought no key" instead of implying the price was licensed.
     */
    private KeySource keySourceFor(String source) {
        if (source == null) {
            return KeySource.NONE;
        }
        if (ProviderCredentials.forProvider(source).isPresent()) {
            return KeySource.USER;
        }
        if (source.equalsIgnoreCase("YAHOO") || source.equalsIgnoreCase("DEMO") || source.equalsIgnoreCase("BINANCE")) {
            return KeySource.NONE;
        }
        return KeySource.APP;
    }

    private QuoteStatus statusFor(LocalDate tradeDate, LocalDate expectedSession) {
        if (tradeDate == null) {
            return QuoteStatus.UNAVAILABLE;
        }
        return tradeDate.isBefore(expectedSession) ? QuoteStatus.STALE : QuoteStatus.FRESH;
    }

    private int staleDays(LocalDate tradeDate, LocalDate expectedSession, AssetClass assetClass) {
        if (tradeDate == null || !tradeDate.isBefore(expectedSession)) {
            return 0;
        }
        if (assetClass == AssetClass.CRYPTO) {
            return (int) ChronoUnit.DAYS.between(tradeDate, expectedSession);
        }
        return calendarService.tradingDaysBetween(tradeDate.plusDays(1), expectedSession).size();
    }

    public LocalDate expectedSessionFor(AssetClass assetClass) {
        if (assetClass == AssetClass.CRYPTO) {
            return LocalDate.now(clock.withZone(ZoneOffset.UTC));
        }
        return lastClosedSession();
    }

    /**
     * The most recent session whose close has passed — the yardstick FRESH is
     * measured against.
     *
     * <p>Today does not count until the bell: at 11am on a trading day the latest
     * available close is yesterday's, so calling today's absence "stale" would
     * label perfectly current data as degraded and train consumers to ignore the
     * label. Early closes are honoured via the existing calendar.
     */
    LocalDate lastClosedSession() {
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(EXCHANGE_ZONE));
        LocalDate cursor = now.toLocalDate();

        boolean todayHasClosed = calendarService.isTradingDay(cursor)
                && !now.toLocalTime().isBefore(calendarService.isEarlyClose(cursor) ? EARLY_CLOSE : REGULAR_CLOSE);

        if (!todayHasClosed) {
            cursor = cursor.minusDays(1);
        }
        int guard = 0;
        while (!calendarService.isTradingDay(cursor) && guard++ < 14) {
            cursor = cursor.minusDays(1);
        }
        return cursor;
    }
}
