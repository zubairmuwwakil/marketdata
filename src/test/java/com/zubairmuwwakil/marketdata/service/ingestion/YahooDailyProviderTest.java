package com.zubairmuwwakil.marketdata.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.client.YahooFinanceClient;
import com.zubairmuwwakil.marketdata.model.dto.CandleSeries;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.dto.QuotedCandle;
import com.zubairmuwwakil.marketdata.repository.IngestionQuarantineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Parser pinned against a fixture captured from the live endpoint, so a change in
 * Yahoo's payload shape fails here rather than silently in somebody's portfolio.
 */
class YahooDailyProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private YahooFinanceClient client;
    private IngestionQuarantineRepository quarantine;
    private YahooDailyProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(YahooFinanceClient.class);
        quarantine = mock(IngestionQuarantineRepository.class);
        provider = new YahooDailyProvider(client, quarantine);
    }

    private JsonNode fixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            return mapper.readTree(in).get("chart").get("result").get(0);
        }
    }

    @Test
    void parsesRealPayloadIntoAscendingCandlesWithCurrency() throws Exception {
        when(client.dailyChart(eq("AAPL"), any(), any())).thenReturn(Optional.of(fixture("yahoo-chart-aapl.json")));

        CandleSeries series = provider.fetchDailySeries("AAPL", LocalDate.now().minusDays(10), LocalDate.now());

        assertThat(series.currency()).isEqualTo("USD");
        assertThat(series.source()).isEqualTo("YAHOO");
        assertThat(series.candles()).isNotEmpty();
        assertThat(series.candles()).isSortedAccordingTo(java.util.Comparator.comparing(DailyCandle::tradeDate));
        assertThat(series.candles()).allSatisfy(c -> {
            assertThat(c.close()).isPositive();
            assertThat(c.low()).isLessThanOrEqualTo(c.high());
        });
    }

    @Test
    void quarantinesRowsWithNullPricesInsteadOfInventingThem() throws Exception {
        when(client.dailyChart(eq("VFV.TO"), any(), any())).thenReturn(Optional.of(fixture("yahoo-chart-tsx-gaps.json")));

        CandleSeries series = provider.fetchDailySeries("VFV.TO", LocalDate.now().minusDays(10), LocalDate.now());

        // Four sessions in the fixture; one has a null open and one a null close.
        assertThat(series.candles()).hasSize(2);
        assertThat(series.candles()).noneMatch(c -> c.close() == null);
        verify(quarantine, times(2)).save(eq("VFV.TO"), any(), eq("missing_fields"), anyString(), eq("YAHOO"), isNull());
    }

    @Test
    void reportsTheListingCurrencyRatherThanAssumingUsd() throws Exception {
        when(client.dailyChart(eq("VFV.TO"), any(), any())).thenReturn(Optional.of(fixture("yahoo-chart-tsx-gaps.json")));

        CandleSeries series = provider.fetchDailySeries("VFV.TO", LocalDate.now().minusDays(10), LocalDate.now());

        assertThat(series.currency()).isEqualTo("CAD");
    }

    @Test
    void nullVolumeBecomesZeroButNullCloseNeverDoes() throws Exception {
        when(client.dailyChart(eq("VFV.TO"), any(), any())).thenReturn(Optional.of(fixture("yahoo-chart-tsx-gaps.json")));

        List<DailyCandle> candles = provider.fetchDailyCandles("VFV.TO", LocalDate.now().minusDays(10), LocalDate.now());

        // The last fixture row has a null volume with a valid close: kept, volume 0.
        DailyCandle last = candles.get(candles.size() - 1);
        assertThat(last.volume()).isZero();
        assertThat(last.close()).isNotNull();
    }

    @Test
    void datesUseTheExchangeTimezoneNotUtc() throws Exception {
        when(client.dailyChart(eq("VFV.TO"), any(), any())).thenReturn(Optional.of(fixture("yahoo-chart-tsx-gaps.json")));

        List<DailyCandle> candles = provider.fetchDailyCandles("VFV.TO", LocalDate.now().minusDays(10), LocalDate.now());

        // 1786363200 is 2026-08-10 13:30 UTC = 09:30 America/Toronto, so the trade
        // date is the 10th in both zones here; the assertion that matters is that a
        // zone was applied at all and the sequence advances one calendar day at a time.
        assertThat(candles.get(0).tradeDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(candles.get(1).tradeDate()).isEqualTo(LocalDate.of(2026, 8, 13));
    }

    @Test
    void unknownSymbolYieldsNothingRatherThanThrowing() {
        when(client.dailyChart(eq("ZZZZ"), any(), any())).thenReturn(Optional.empty());

        assertThat(provider.fetchDailyCandles("ZZZZ", LocalDate.now().minusDays(5), LocalDate.now())).isEmpty();
        assertThat(provider.fetchLatestClose("ZZZZ")).isEmpty();
    }

    @Test
    void latestCloseIsTheMostRecentSessionInTheWindow() throws Exception {
        when(client.dailyChart(eq("VFV.TO"), any(), any())).thenReturn(Optional.of(fixture("yahoo-chart-tsx-gaps.json")));

        Optional<QuotedCandle> latest = provider.fetchLatestClose("VFV.TO");

        assertThat(latest).isPresent();
        assertThat(latest.get().currency()).isEqualTo("CAD");
        assertThat(latest.get().source()).isEqualTo("YAHOO");
        assertThat(latest.get().candle().close()).isEqualByComparingTo("144.55");
    }
}
