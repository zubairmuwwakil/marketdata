package com.zubairmuwwakil.marketdata.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.client.BinanceClient;
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

class BinanceCryptoProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private BinanceClient client;
    private IngestionQuarantineRepository quarantine;
    private BinanceCryptoProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(BinanceClient.class);
        quarantine = mock(IngestionQuarantineRepository.class);
        provider = new BinanceCryptoProvider(client, quarantine);
    }

    @Test
    void declaresCryptoSupportAndBinanceSource() {
        assertThat(provider.sourceName()).isEqualTo("BINANCE");
        assertThat(provider.supportedAssetClasses()).containsExactly(AssetClass.CRYPTO);
    }

    @Test
    void parses24hrTickerIntoQuotedCandleWithUsdtCurrency() throws Exception {
        String json = """
                {
                  "symbol": "BTCUSDT",
                  "openPrice": "62000.00",
                  "highPrice": "63500.50",
                  "lowPrice": "61800.00",
                  "lastPrice": "62899.75",
                  "volume": "15234.5",
                  "closeTime": 1723939200000
                }
                """;
        JsonNode node = mapper.readTree(json);
        when(client.ticker24hr(eq("BTC"))).thenReturn(Optional.of(node));

        Optional<QuotedCandle> quote = provider.fetchLatestClose("BTC");

        assertThat(quote).isPresent();
        assertThat(quote.get().currency()).isEqualTo("USDT");
        assertThat(quote.get().source()).isEqualTo("BINANCE");
        assertThat(quote.get().candle().close()).isEqualByComparingTo("62899.75");
        assertThat(quote.get().candle().high()).isEqualByComparingTo("63500.50");
        assertThat(quote.get().candle().low()).isEqualByComparingTo("61800.00");
    }

    @Test
    void parsesKlinesIntoAscendingCandles() throws Exception {
        String json = """
                [
                  [ 1723852800000, "61000.00", "62500.00", "60500.00", "62000.00", "12000.5", 1723939199999 ],
                  [ 1723939200000, "62000.00", "63500.50", "61800.00", "62899.75", "15234.5", 1724025599999 ]
                ]
                """;
        JsonNode node = mapper.readTree(json);
        when(client.dailyKlines(eq("BTC"), any(), any())).thenReturn(Optional.of(node));

        CandleSeries series = provider.fetchDailySeries("BTC", LocalDate.of(2024, 8, 17), LocalDate.of(2024, 8, 18));

        assertThat(series.currency()).isEqualTo("USDT");
        assertThat(series.source()).isEqualTo("BINANCE");
        assertThat(series.candles()).hasSize(2);
        assertThat(series.candles()).isSortedAccordingTo(java.util.Comparator.comparing(DailyCandle::tradeDate));
        assertThat(series.candles().get(0).close()).isEqualByComparingTo("62000.00");
        assertThat(series.candles().get(1).close()).isEqualByComparingTo("62899.75");
    }

    @Test
    void unknownSymbolYieldsNothing() {
        when(client.ticker24hr(eq("UNKNOWN"))).thenReturn(Optional.empty());
        when(client.dailyKlines(eq("UNKNOWN"), any(), any())).thenReturn(Optional.empty());

        assertThat(provider.fetchLatestClose("UNKNOWN")).isEmpty();
        assertThat(provider.fetchDailyCandles("UNKNOWN", LocalDate.now().minusDays(5), LocalDate.now())).isEmpty();
    }
}
