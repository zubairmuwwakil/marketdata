package com.zubairmuwwakil.marketdata.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.zubairmuwwakil.marketdata.client.QuestradeClient;
import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.CandleSeries;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.dto.QuotedCandle;
import com.zubairmuwwakil.marketdata.repository.IngestionQuarantineRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Questrade-sourced market data provider for US and Canadian equities.
 *
 * <p>Implements both {@link MarketDataProvider} and {@link LatestQuoteProvider}.
 * Quotes and candles carry explicit listing currency (USD/CAD) as reported by
 * Questrade symbol search.
 */
@Service
@Profile("!demo")
public class QuestradeQuoteProvider implements MarketDataProvider, LatestQuoteProvider {

    public static final String SOURCE_NAME = "QUESTRADE";
    private static final int QUOTE_SCALE = 4;
    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    private final QuestradeClient client;
    private final IngestionQuarantineRepository quarantineRepository;

    public QuestradeQuoteProvider(QuestradeClient client,
                                  IngestionQuarantineRepository quarantineRepository) {
        this.client = client;
        this.quarantineRepository = quarantineRepository;
    }

    @Override
    public List<DailyCandle> fetchDailyCandles(String symbol, LocalDate from, LocalDate to) {
        Optional<QuestradeClient.QuestradeSymbol> sym = client.searchSymbol(symbol);
        if (sym.isEmpty()) {
            return List.of();
        }

        return client.dailyCandles(sym.get().symbolId(), from, to)
                .map(node -> parseCandles(symbol, node))
                .orElseGet(List::of);
    }

    @Override
    public CandleSeries fetchDailySeries(String symbol, LocalDate from, LocalDate to) {
        List<DailyCandle> candles = fetchDailyCandles(symbol, from, to);
        String currency = priceCurrency(symbol);
        return new CandleSeries(symbol, currency, SOURCE_NAME, candles);
    }

    @Override
    public Optional<QuotedCandle> fetchLatestClose(String symbol) {
        Optional<QuestradeClient.QuestradeSymbol> sym = client.searchSymbol(symbol);
        if (sym.isEmpty()) {
            return Optional.empty();
        }

        QuestradeClient.QuestradeSymbol symbolMeta = sym.get();
        return client.quote(symbolMeta.symbolId()).flatMap(node -> {
            try {
                BigDecimal close = parseDecimal(node.has("lastTradePrice") ? node.get("lastTradePrice") : null);
                if (close == null && node.has("lastTradePriceTrHrs")) {
                    close = parseDecimal(node.get("lastTradePriceTrHrs"));
                }
                BigDecimal open = parseDecimal(node.has("openPrice") ? node.get("openPrice") : null);
                BigDecimal high = parseDecimal(node.has("highPrice") ? node.get("highPrice") : null);
                BigDecimal low = parseDecimal(node.has("lowPrice") ? node.get("lowPrice") : null);
                long volume = parseVolume(node.has("volume") ? node.get("volume") : null);

                if (close == null) {
                    return Optional.empty();
                }

                // If open/high/low are missing from single quote, bound them by close
                if (open == null) open = close;
                if (high == null) high = close;
                if (low == null) low = close;

                LocalDate tradeDate = parseTradeDate(node.get("lastTradeTime"));
                DailyCandle candle = new DailyCandle(tradeDate, open, high, low, close, volume);
                return Optional.of(new QuotedCandle(symbol, candle, symbolMeta.currency(), SOURCE_NAME));
            } catch (Exception ex) {
                return Optional.empty();
            }
        });
    }

    @Override
    public String priceCurrency(String symbol) {
        return client.searchSymbol(symbol)
                .map(QuestradeClient.QuestradeSymbol::currency)
                .orElse("USD");
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public Set<AssetClass> supportedAssetClasses() {
        return Set.of(AssetClass.EQUITY);
    }

    private List<DailyCandle> parseCandles(String symbol, JsonNode root) {
        if (!root.isArray()) return List.of();
        List<DailyCandle> out = new ArrayList<>();

        for (int i = 0; i < root.size(); i++) {
            JsonNode entry = root.get(i);
            LocalDate tradeDate = parseTradeDate(entry.get("start"));

            BigDecimal open = parseDecimal(entry.get("open"));
            BigDecimal high = parseDecimal(entry.get("high"));
            BigDecimal low = parseDecimal(entry.get("low"));
            BigDecimal close = parseDecimal(entry.get("close"));
            long volume = parseVolume(entry.get("volume"));

            if (open == null || high == null || low == null || close == null || tradeDate == null) {
                quarantineRepository.save(symbol, tradeDate, "missing_fields",
                        "{\"index\":" + i + ",\"reason\":\"null OHLC in Questrade candle\"}", SOURCE_NAME, null);
                continue;
            }

            out.add(new DailyCandle(tradeDate, open, high, low, close, volume));
        }

        out.sort(Comparator.comparing(DailyCandle::tradeDate));
        return out;
    }

    private LocalDate parseTradeDate(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return LocalDate.now(NY_ZONE);
        }
        try {
            return ZonedDateTime.parse(node.asText()).withZoneSameInstant(NY_ZONE).toLocalDate();
        } catch (Exception ex) {
            return LocalDate.now(NY_ZONE);
        }
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
