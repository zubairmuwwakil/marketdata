package com.zubairmuwwakil.marketdata.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.zubairmuwwakil.marketdata.client.BinanceClient;
import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.CandleSeries;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.dto.QuotedCandle;
import com.zubairmuwwakil.marketdata.repository.IngestionQuarantineRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Binance-sourced cryptocurrency daily closes and history.
 *
 * <p>Serves the cryptocurrency asset class across both capabilities:
 * <ul>
 *   <li>{@link MarketDataProvider} for curated or dynamic crypto history backfills.</li>
 *   <li>{@link LatestQuoteProvider} for the dynamic quote path.</li>
 * </ul>
 *
 * <p>Crypto trades 24/7/365 in UTC. Pricing is quoted in USDT (or pair quote currency)
 * rather than USD, preserving currency precision without fabricated FX assumptions.
 */
@Service
@Profile("!demo")
public class BinanceCryptoProvider implements MarketDataProvider, LatestQuoteProvider {

    public static final String SOURCE_NAME = "BINANCE";
    private static final int QUOTE_SCALE = 6;

    private final BinanceClient client;
    private final IngestionQuarantineRepository quarantineRepository;

    public BinanceCryptoProvider(BinanceClient client,
                                 IngestionQuarantineRepository quarantineRepository) {
        this.client = client;
        this.quarantineRepository = quarantineRepository;
    }

    @Override
    public List<DailyCandle> fetchDailyCandles(String symbol, LocalDate from, LocalDate to) {
        return client.dailyKlines(symbol, from, to)
                .map(node -> parseKlines(symbol, node))
                .orElseGet(List::of);
    }

    @Override
    public CandleSeries fetchDailySeries(String symbol, LocalDate from, LocalDate to) {
        List<DailyCandle> candles = fetchDailyCandles(symbol, from, to);
        String currency = BinanceClient.resolveCurrency(symbol);
        return new CandleSeries(symbol, currency, SOURCE_NAME, candles);
    }

    @Override
    public Optional<QuotedCandle> fetchLatestClose(String symbol) {
        return client.ticker24hr(symbol).flatMap(node -> {
            try {
                BigDecimal open = parseDecimal(node.get("openPrice"));
                BigDecimal high = parseDecimal(node.get("highPrice"));
                BigDecimal low = parseDecimal(node.get("lowPrice"));
                BigDecimal close = parseDecimal(node.get("lastPrice"));
                long volume = parseVolume(node.get("volume"));

                if (close == null || open == null || high == null || low == null) {
                    return Optional.empty();
                }

                long closeTime = node.has("closeTime") ? node.get("closeTime").asLong() : System.currentTimeMillis();
                LocalDate tradeDate = Instant.ofEpochMilli(closeTime)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate();

                DailyCandle candle = new DailyCandle(tradeDate, open, high, low, close, volume);
                String currency = BinanceClient.resolveCurrency(symbol);
                return Optional.of(new QuotedCandle(symbol, candle, currency, SOURCE_NAME));
            } catch (Exception ex) {
                return Optional.empty();
            }
        });
    }

    @Override
    public String priceCurrency(String symbol) {
        return BinanceClient.resolveCurrency(symbol);
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public Set<AssetClass> supportedAssetClasses() {
        return Set.of(AssetClass.CRYPTO);
    }

    private List<DailyCandle> parseKlines(String symbol, JsonNode root) {
        if (!root.isArray()) return List.of();
        List<DailyCandle> out = new ArrayList<>();

        for (int i = 0; i < root.size(); i++) {
            JsonNode entry = root.get(i);
            if (!entry.isArray() || entry.size() < 6) continue;

            long openTime = entry.get(0).asLong();
            LocalDate tradeDate = Instant.ofEpochMilli(openTime).atZone(ZoneOffset.UTC).toLocalDate();

            BigDecimal open = parseDecimal(entry.get(1));
            BigDecimal high = parseDecimal(entry.get(2));
            BigDecimal low = parseDecimal(entry.get(3));
            BigDecimal close = parseDecimal(entry.get(4));
            long volume = parseVolume(entry.get(5));

            if (open == null || high == null || low == null || close == null) {
                quarantineRepository.save(symbol, tradeDate, "missing_fields",
                        "{\"index\":" + i + ",\"reason\":\"null OHLC in Binance kline\"}", SOURCE_NAME, null);
                continue;
            }

            out.add(new DailyCandle(tradeDate, open, high, low, close, volume));
        }

        out.sort(Comparator.comparing(DailyCandle::tradeDate));
        return out;
    }

    private BigDecimal parseDecimal(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return new BigDecimal(node.asText()).setScale(QUOTE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        } catch (Exception ex) {
            return null;
        }
    }

    private long parseVolume(JsonNode node) {
        if (node == null || node.isNull()) return 0L;
        try {
            return new BigDecimal(node.asText()).longValue();
        } catch (Exception ex) {
            return 0L;
        }
    }
}
