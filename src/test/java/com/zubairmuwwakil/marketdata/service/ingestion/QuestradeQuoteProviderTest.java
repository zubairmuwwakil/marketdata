package com.zubairmuwwakil.marketdata.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.client.QuestradeClient;
import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.CandleSeries;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.dto.QuotedCandle;
import com.zubairmuwwakil.marketdata.repository.IngestionQuarantineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QuestradeQuoteProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private QuestradeClient client;
    private IngestionQuarantineRepository quarantine;
    private QuestradeQuoteProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(QuestradeClient.class);
        quarantine = mock(IngestionQuarantineRepository.class);
        provider = new QuestradeQuoteProvider(client, quarantine);
    }

    @Test
    void declaresEquitySupportAndQuestradeSource() {
        assertThat(provider.sourceName()).isEqualTo("QUESTRADE");
        assertThat(provider.supportedAssetClasses()).containsExactly(AssetClass.EQUITY);
    }

    @Test
    void parsesQuoteAndReportsAccurateCadCurrencyForTsxSymbol() throws Exception {
        when(client.searchSymbol(eq("VFV.TO")))
                .thenReturn(Optional.of(new QuestradeClient.QuestradeSymbol(26543, "VFV.TO", "CAD")));

        String quoteJson = """
                {
                  "quotes": [
                    {
                      "symbol": "VFV.TO",
                      "symbolId": 26543,
                      "lastTradePrice": 142.50,
                      "openPrice": 141.80,
                      "highPrice": 143.00,
                      "lowPrice": 141.50,
                      "volume": 250000,
                      "lastTradeTime": "2026-08-18T16:00:00.000000-04:00"
                    }
                  ]
                }
                """;
        JsonNode quoteNode = mapper.readTree(quoteJson).get("quotes").get(0);
        when(client.quote(eq(26543))).thenReturn(Optional.of(quoteNode));

        Optional<QuotedCandle> quote = provider.fetchLatestClose("VFV.TO");

        assertThat(quote).isPresent();
        assertThat(quote.get().currency()).isEqualTo("CAD");
        assertThat(quote.get().source()).isEqualTo("QUESTRADE");
        assertThat(quote.get().candle().close()).isEqualByComparingTo("142.50");
        assertThat(quote.get().candle().open()).isEqualByComparingTo("141.80");
        assertThat(quote.get().candle().high()).isEqualByComparingTo("143.00");
        assertThat(quote.get().candle().low()).isEqualByComparingTo("141.50");
    }

    @Test
    void parsesDailyCandlesForUsListingInUsd() throws Exception {
        when(client.searchSymbol(eq("AAPL")))
                .thenReturn(Optional.of(new QuestradeClient.QuestradeSymbol(8049, "AAPL", "USD")));

        String candlesJson = """
                {
                  "candles": [
                    {
                      "start": "2026-08-17T00:00:00.000000-04:00",
                      "end": "2026-08-17T16:00:00.000000-04:00",
                      "open": 222.0,
                      "high": 225.0,
                      "low": 221.0,
                      "close": 224.5,
                      "volume": 40000000
                    },
                    {
                      "start": "2026-08-18T00:00:00.000000-04:00",
                      "end": "2026-08-18T16:00:00.000000-04:00",
                      "open": 224.0,
                      "high": 228.0,
                      "low": 223.5,
                      "close": 227.0,
                      "volume": 45000000
                    }
                  ]
                }
                """;
        JsonNode candlesNode = mapper.readTree(candlesJson).get("candles");
        when(client.dailyCandles(eq(8049), any(), any())).thenReturn(Optional.of(candlesNode));

        CandleSeries series = provider.fetchDailySeries("AAPL", LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18));

        assertThat(series.currency()).isEqualTo("USD");
        assertThat(series.source()).isEqualTo("QUESTRADE");
        assertThat(series.candles()).hasSize(2);
        assertThat(series.candles().get(0).tradeDate()).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(series.candles().get(1).tradeDate()).isEqualTo(LocalDate.of(2026, 8, 18));
    }
}
