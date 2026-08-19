package com.zubairmuwwakil.marketdata.service.quote;

import com.zubairmuwwakil.marketdata.config.MarketCalendarProperties;
import com.zubairmuwwakil.marketdata.config.ProviderProperties;
import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.KeySource;
import com.zubairmuwwakil.marketdata.model.dto.*;
import com.zubairmuwwakil.marketdata.repository.LatestCandleRepository;
import com.zubairmuwwakil.marketdata.repository.PriceCandleUpsertRepository;
import com.zubairmuwwakil.marketdata.repository.TrackedSymbolRepository;
import com.zubairmuwwakil.marketdata.service.calendar.MarketCalendarService;
import com.zubairmuwwakil.marketdata.service.ingestion.LatestQuoteProvider;
import com.zubairmuwwakil.marketdata.service.ingestion.MarketDataProviderRegistry;
import com.zubairmuwwakil.marketdata.service.ingestion.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The invariant under test throughout: valuation never hard-depends on a live
 * fetch. Cache last-known, label staleness, fail closed rather than fabricate.
 */
class QuoteServiceTest {

    private static final ZoneId NY = ZoneId.of("America/New_York");
    /** Thursday 2026-08-13, 17:00 New York — after the close. */
    private static final Instant AFTER_CLOSE =
            ZonedDateTime.of(2026, 8, 13, 17, 0, 0, 0, NY).toInstant();
    /** Same day, 11:00 New York — before the close, so the latest close is Wednesday. */
    private static final Instant BEFORE_CLOSE =
            ZonedDateTime.of(2026, 8, 13, 11, 0, 0, 0, NY).toInstant();

    private LatestCandleRepository latestCandles;
    private PriceCandleUpsertRepository upserts;
    private TrackedSymbolRepository tracked;
    private QuotaService quota;
    private MarketDataProviderRegistry registry;
    private LatestQuoteProvider provider;
    private ProviderProperties providerProperties;
    private MarketCalendarService calendar;

    @BeforeEach
    void setUp() {
        latestCandles = mock(LatestCandleRepository.class);
        upserts = mock(PriceCandleUpsertRepository.class);
        tracked = mock(TrackedSymbolRepository.class);
        quota = mock(QuotaService.class);
        registry = mock(MarketDataProviderRegistry.class);
        provider = mock(LatestQuoteProvider.class);
        providerProperties = new ProviderProperties();
        calendar = new MarketCalendarService(new MarketCalendarProperties());

        when(provider.sourceName()).thenReturn("YAHOO");
        when(registry.quoteProvider(any())).thenReturn(provider);
        when(quota.tryConsumeOneCall(anyString(), anyInt())).thenReturn(true);
    }

    private QuoteService serviceAt(Instant now) {
        return new QuoteService(registry, latestCandles, upserts, tracked, calendar, quota,
                providerProperties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private LatestCandleRepository.LatestCandle cached(String symbol, LocalDate date, String close, String ccy) {
        return new LatestCandleRepository.LatestCandle(symbol, date, new BigDecimal(close), ccy, "YAHOO");
    }

    @Test
    void expectedSessionIsYesterdayBeforeTodaysBellAndTodayAfterIt() {
        assertThat(serviceAt(BEFORE_CLOSE).lastClosedSession()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(serviceAt(AFTER_CLOSE).lastClosedSession()).isEqualTo(LocalDate.of(2026, 8, 13));
    }

    @Test
    void freshCacheIsServedWithoutCallingTheProviderAtAll() {
        when(latestCandles.findLatestFor(any()))
                .thenReturn(Map.of("AAPL", cached("AAPL", LocalDate.of(2026, 8, 13), "310.03", "USD")));

        QuoteBatch batch = serviceAt(AFTER_CLOSE).quote(List.of("AAPL"), AssetClass.EQUITY);

        assertThat(batch.quotes()).singleElement().satisfies(q -> {
            assertThat(q.status()).isEqualTo(QuoteStatus.FRESH);
            assertThat(q.staleTradingDays()).isZero();
            assertThat(q.currency()).isEqualTo("USD");
        });
        verify(provider, never()).fetchLatestClose(anyString());
    }

    @Test
    void providerFailureDegradesToCachedLastKnownLabelledWithItsAge() {
        when(latestCandles.findLatestFor(any()))
                .thenReturn(Map.of("VFV.TO", cached("VFV.TO", LocalDate.of(2026, 8, 10), "142.30", "CAD")));
        when(provider.fetchLatestClose("VFV.TO")).thenThrow(new RuntimeException("Yahoo 503"));

        QuoteBatch batch = serviceAt(AFTER_CLOSE).quote(List.of("VFV.TO"), AssetClass.EQUITY);

        assertThat(batch.quotes()).singleElement().satisfies(q -> {
            assertThat(q.status()).isEqualTo(QuoteStatus.STALE);
            assertThat(q.close()).isEqualByComparingTo("142.30");
            // Aug 10 Mon -> Aug 13 Thu inclusive of 11, 12, 13.
            assertThat(q.staleTradingDays()).isEqualTo(3);
        });
    }

    @Test
    void nothingCachedAndNothingFetchedFailsClosedWithANullPrice() {
        when(latestCandles.findLatestFor(any())).thenReturn(Map.of());
        when(provider.fetchLatestClose("ZZZZ")).thenReturn(Optional.empty());

        QuoteBatch batch = serviceAt(AFTER_CLOSE).quote(List.of("ZZZZ"), AssetClass.EQUITY);

        assertThat(batch.quotes()).singleElement().satisfies(q -> {
            assertThat(q.status()).isEqualTo(QuoteStatus.UNAVAILABLE);
            assertThat(q.close()).isNull();
            assertThat(q.currency()).isNull();
            assertThat(q.reason()).isEqualTo("no_data");
        });
    }

    @Test
    void aRefreshThatResolvesNothingWritesNothing() {
        when(latestCandles.findLatestFor(any()))
                .thenReturn(Map.of("AAPL", cached("AAPL", LocalDate.of(2026, 8, 10), "300.00", "USD")));
        when(provider.fetchLatestClose("AAPL")).thenReturn(Optional.empty());

        serviceAt(AFTER_CLOSE).quote(List.of("AAPL"), AssetClass.EQUITY);

        // Mirrors the FX cron rule: an empty fetch must never overwrite good data.
        verify(upserts, never()).upsertAll(anyString(), anyList(), anyBoolean(), anyString(), any());
    }

    @Test
    void exhaustedBudgetServesCacheInsteadOfFailingTheRequest() {
        when(quota.tryConsumeOneCall(anyString(), anyInt())).thenReturn(false);
        when(latestCandles.findLatestFor(any()))
                .thenReturn(Map.of("AAPL", cached("AAPL", LocalDate.of(2026, 8, 12), "308.00", "USD")));

        QuoteBatch batch = serviceAt(AFTER_CLOSE).quote(List.of("AAPL"), AssetClass.EQUITY);

        assertThat(batch.quotes()).singleElement().satisfies(q -> {
            assertThat(q.status()).isEqualTo(QuoteStatus.STALE);
            assertThat(q.close()).isEqualByComparingTo("308.00");
        });
        verify(provider, never()).fetchLatestClose(anyString());
    }

    @Test
    void aSuccessfulRefreshPersistsTheCandleWithItsCurrency() {
        when(latestCandles.findLatestFor(any())).thenReturn(Map.of());
        when(provider.fetchLatestClose("VFV.TO")).thenReturn(Optional.of(new QuotedCandle(
                "VFV.TO",
                new DailyCandle(LocalDate.of(2026, 8, 13), new BigDecimal("143.00"), new BigDecimal("145.00"),
                        new BigDecimal("142.00"), new BigDecimal("144.55"), 100L),
                "CAD", "YAHOO")));

        QuoteBatch batch = serviceAt(AFTER_CLOSE).quote(List.of("VFV.TO"), AssetClass.EQUITY);

        assertThat(batch.quotes()).singleElement().satisfies(q -> {
            assertThat(q.status()).isEqualTo(QuoteStatus.FRESH);
            assertThat(q.currency()).isEqualTo("CAD");
            assertThat(q.source()).isEqualTo("YAHOO");
            // Keyless provider: reported as NONE so unlicensed provenance is visible.
            assertThat(q.keySource()).isEqualTo(KeySource.NONE);
        });
        verify(upserts).upsertAll(eq("VFV.TO"), anyList(), eq(false), eq("YAHOO"), eq("CAD"));
    }

    @Test
    void oversizedBatchesReportWhatWasDroppedRatherThanTrimmingSilently() {
        providerProperties.getYahoo().setMaxSymbolsPerRequest(2);
        when(latestCandles.findLatestFor(any())).thenReturn(Map.of());
        when(provider.fetchLatestClose(anyString())).thenReturn(Optional.empty());

        QuoteBatch batch = serviceAt(AFTER_CLOSE).quote(List.of("A", "B", "C", "D"), AssetClass.EQUITY);

        assertThat(batch.quotes()).hasSize(2);
        assertThat(batch.truncated()).containsExactly("C", "D");
    }

    @Test
    void symbolsAreDemandRegisteredOnEveryRequest() {
        when(latestCandles.findLatestFor(any())).thenReturn(Map.of());
        when(provider.fetchLatestClose(anyString())).thenReturn(Optional.empty());

        serviceAt(AFTER_CLOSE).quote(List.of("aapl", " msft "), AssetClass.EQUITY);

        verify(tracked).touch("AAPL", "EQUITY");
        verify(tracked).touch("MSFT", "EQUITY");
    }

    @Test
    void thePayloadLabelsItselfAsDailyCloseNeverRealTime() {
        when(latestCandles.findLatestFor(any())).thenReturn(Map.of());
        when(provider.fetchLatestClose(anyString())).thenReturn(Optional.empty());

        QuoteBatch batch = serviceAt(AFTER_CLOSE).quote(List.of("AAPL"), AssetClass.EQUITY);

        assertThat(batch.pricing()).isEqualTo("daily-close");
        assertThat(batch.expectedSession()).isEqualTo(LocalDate.of(2026, 8, 13));
    }

    @Test
    void noQuoteProviderForTheAssetClassStillServesCacheRatherThanErroring() {
        when(registry.quoteProvider(any())).thenThrow(new IllegalStateException("no crypto provider registered"));
        when(latestCandles.findLatestFor(any()))
                .thenReturn(Map.of("BTC", cached("BTC", LocalDate.of(2026, 8, 11), "89644.00", "CAD")));

        QuoteBatch batch = serviceAt(AFTER_CLOSE).quote(List.of("BTC"), AssetClass.CRYPTO);

        assertThat(batch.quotes()).singleElement().satisfies(q -> {
            assertThat(q.status()).isEqualTo(QuoteStatus.STALE);
            assertThat(q.close()).isEqualByComparingTo("89644.00");
        });
    }
}
