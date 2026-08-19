package com.zubairmuwwakil.marketdata.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BinanceClientTest {

    @Test
    void normalizesVariousCryptoSymbolFormats() {
        assertThat(BinanceClient.normalizePair("BTC")).isEqualTo("BTCUSDT");
        assertThat(BinanceClient.normalizePair("btc")).isEqualTo("BTCUSDT");
        assertThat(BinanceClient.normalizePair("ETH-USDT")).isEqualTo("ETHUSDT");
        assertThat(BinanceClient.normalizePair("SOL/USDT")).isEqualTo("SOLUSDT");
        assertThat(BinanceClient.normalizePair("BTC-USD")).isEqualTo("BTCUSDT");
        assertThat(BinanceClient.normalizePair("ETHUSD")).isEqualTo("ETHUSDT");
        assertThat(BinanceClient.normalizePair("BTCEUR")).isEqualTo("BTCEUR");
        assertThat(BinanceClient.normalizePair("SOLUSDC")).isEqualTo("SOLUSDC");
    }

    @Test
    void resolvesCurrencyFromPair() {
        assertThat(BinanceClient.resolveCurrency("BTC")).isEqualTo("USDT");
        assertThat(BinanceClient.resolveCurrency("BTC-USD")).isEqualTo("USDT");
        assertThat(BinanceClient.resolveCurrency("BTCEUR")).isEqualTo("EUR");
        assertThat(BinanceClient.resolveCurrency("SOLUSDC")).isEqualTo("USDC");
        assertThat(BinanceClient.resolveCurrency("ETHCAD")).isEqualTo("CAD");
    }
}
