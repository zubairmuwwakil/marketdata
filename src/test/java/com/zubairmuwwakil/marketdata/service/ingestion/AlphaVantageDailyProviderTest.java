package com.zubairmuwwakil.marketdata.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.client.AlphaVantageClient;
import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.dto.QuotedCandle;
import com.zubairmuwwakil.marketdata.repository.IngestionQuarantineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlphaVantageDailyProviderTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-14T12:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper mapper = new ObjectMapper();
    private AlphaVantageClient client;
    private IngestionQuarantineRepository quarantine;
    private AlphaVantageDailyProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(AlphaVantageClient.class);
        quarantine = mock(IngestionQuarantineRepository.class);
        provider = new AlphaVantageDailyProvider(client, quarantine, FIXED_CLOCK);
    }

    @Test
    void declaresEquitySupportAndAlphaVantageSource() {
        assertThat(provider.sourceName()).isEqualTo("ALPHAVANTAGE");
        assertThat(provider.supportedAssetClasses()).containsExactly(AssetClass.EQUITY);
        assertThat(provider.priceCurrency("AAPL")).isEqualTo("USD");
    }

    @Test
    void parsesDailyCandlesAndResolvesLatestClose() throws Exception {
        LocalDate firstDate = LocalDate.now(FIXED_CLOCK).minusDays(2);
        LocalDate lastDate = firstDate.plusDays(1);
        String json = """
                {
                  "Meta Data": {
                    "2. Symbol": "AAPL"
                  },
                  "Time Series (Daily)": {
                    "%s": {
                      "1. open": "220.00",
                      "2. high": "225.00",
                      "3. low": "219.00",
                      "4. close": "223.50",
                      "5. volume": "50000000"
                    },
                    "%s": {
                      "1. open": "224.00",
                      "2. high": "228.00",
                      "3. low": "223.00",
                      "4. close": "227.80",
                      "5. volume": "60000000"
                    }
                  }
                }
                """.formatted(firstDate, lastDate);
        JsonNode node = mapper.readTree(json);
        when(client.timeSeriesDaily(eq("AAPL"))).thenReturn(node);

        List<DailyCandle> candles = provider.fetchDailyCandles("AAPL", firstDate, lastDate);
        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).tradeDate()).isEqualTo(firstDate);
        assertThat(candles.get(1).tradeDate()).isEqualTo(lastDate);

        Optional<QuotedCandle> quote = provider.fetchLatestClose("AAPL");
        assertThat(quote).isPresent();
        assertThat(quote.get().currency()).isEqualTo("USD");
        assertThat(quote.get().source()).isEqualTo("ALPHAVANTAGE");
        assertThat(quote.get().candle().close()).isEqualByComparingTo("227.80");
        assertThat(quote.get().candle().tradeDate()).isEqualTo(lastDate);
    }

    @Test
    void latestCloseIgnoresCandlesOutsideThirtyDayWindow() throws Exception {
        LocalDate expiredDate = LocalDate.now(FIXED_CLOCK).minusDays(31);
        JsonNode node = mapper.readTree("""
                {
                  "Time Series (Daily)": {
                    "%s": {
                      "1. open": "220.00",
                      "2. high": "225.00",
                      "3. low": "219.00",
                      "4. close": "223.50",
                      "5. volume": "50000000"
                    }
                  }
                }
                """.formatted(expiredDate));
        when(client.timeSeriesDaily(eq("AAPL"))).thenReturn(node);

        assertThat(provider.fetchLatestClose("AAPL")).isEmpty();
    }
}
