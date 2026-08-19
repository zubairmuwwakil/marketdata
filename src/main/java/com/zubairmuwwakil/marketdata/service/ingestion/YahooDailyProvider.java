package com.zubairmuwwakil.marketdata.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.zubairmuwwakil.marketdata.client.YahooFinanceClient;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Yahoo-sourced daily candles (Amendment E4), serving the dynamic per-user symbol
 * set that Alpha Vantage's 25-calls-per-day free tier cannot.
 *
 * <p>Implements both capabilities: history for backfills, and latest-close for the
 * quote path. Provenance rides {@link #sourceName()} into {@code price_candle.source}
 * exactly as E4 specified.
 *
 * <p>Needs no credential, which is why it is the last link in the BYOK resolution
 * chain: a caller who brings nothing still gets a price, labelled
 * {@code keySource=NONE} so the unlicensed provenance is visible rather than
 * assumed.
 */
@Service
@Profile("!demo")
public class YahooDailyProvider implements MarketDataProvider, LatestQuoteProvider {

    public static final String SOURCE_NAME = "YAHOO";

    /** Lookback for "latest close": long enough to clear a long weekend plus a
     *  holiday, short enough not to haul a year of history for one price. */
    private static final int LATEST_LOOKBACK_DAYS = 10;

    /**
     * Yahoo serialises prices from 32-bit floats, so a $310.03 close arrives as
     * 310.02999877929688 and a $189.70 close as 189.69999694824219. Four decimal
     * places is below float32's precision at equity price magnitudes but at or
     * above the finest increment equities actually trade in, so it removes the
     * serialisation noise without discarding any real precision. This is undoing a
     * transport artefact, not inventing accuracy.
     */
    private static final int QUOTE_SCALE = 4;

    private final YahooFinanceClient client;
    private final IngestionQuarantineRepository quarantineRepository;

    public YahooDailyProvider(YahooFinanceClient client,
                              IngestionQuarantineRepository quarantineRepository) {
        this.client = client;
        this.quarantineRepository = quarantineRepository;
    }

    @Override
    public List<DailyCandle> fetchDailyCandles(String symbol, LocalDate from, LocalDate to) {
        String querySymbol = normalizeSymbol(symbol);
        return client.dailyChart(querySymbol, from, to)
                .map(result -> parse(symbol, result).candles())
                .orElseGet(List::of);
    }

    /** Overridden so the currency Yahoo already told us in the same response is
     *  not thrown away and re-fetched. */
    @Override
    public CandleSeries fetchDailySeries(String symbol, LocalDate from, LocalDate to) {
        String querySymbol = normalizeSymbol(symbol);
        return client.dailyChart(querySymbol, from, to)
                .map(result -> {
                    ParsedSeries parsed = parse(symbol, result);
                    return new CandleSeries(symbol, parsed.currency(), SOURCE_NAME, parsed.candles());
                })
                .orElseGet(() -> new CandleSeries(symbol, null, SOURCE_NAME, List.of()));
    }

    @Override
    public Optional<QuotedCandle> fetchLatestClose(String symbol) {
        LocalDate to = LocalDate.now(ZoneId.of("America/New_York"));
        LocalDate from = to.minusDays(LATEST_LOOKBACK_DAYS);
        String querySymbol = normalizeSymbol(symbol);

        return client.dailyChart(querySymbol, from, to).flatMap(result -> {
            ParsedSeries series = parse(symbol, result);
            if (series.candles().isEmpty()) {
                return Optional.empty();
            }
            DailyCandle latest = series.candles().get(series.candles().size() - 1);
            return Optional.of(new QuotedCandle(symbol, latest, series.currency(), SOURCE_NAME));
        });
    }

    public static String normalizeSymbol(String symbol) {
        if (symbol == null) return "";
        String s = symbol.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("TSE:") || s.startsWith("TSX:")) {
            s = s.substring(4) + ".TO";
        } else if (s.endsWith("-TO") || s.endsWith(":CA") || s.endsWith(":TO")) {
            s = s.replaceAll("(-TO|:CA|:TO)$", ".TO");
        }
        return s;
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public Set<AssetClass> supportedAssetClasses() {
        return Set.of(AssetClass.EQUITY);
    }

    /** A parsed series and the currency Yahoo says it is quoted in. */
    private record ParsedSeries(String currency, List<DailyCandle> candles) {}

    /**
     * Maps one {@code chart.result[0]} node to candles.
     *
     * <p>Two details that are easy to get wrong and expensive to get wrong:
     * <ul>
     *   <li><strong>Timezone.</strong> Each timestamp is the session's open instant.
     *       Converting it in UTC assigns the wrong calendar day for any exchange far
     *       enough east or west, so the exchange's own timezone from the metadata
     *       decides the trade date.</li>
     *   <li><strong>Nulls.</strong> Yahoo pads its arrays and emits nulls for
     *       sessions it has no data for. A null close is quarantined, never carried
     *       forward from the previous day and never zero-filled — a fabricated
     *       close would flow straight into somebody's portfolio total.</li>
     * </ul>
     */
    private ParsedSeries parse(String symbol, JsonNode result) {
        JsonNode meta = result.get("meta");
        String currency = meta == null || meta.get("currency") == null || meta.get("currency").isNull()
                ? null
                : meta.get("currency").asText().toUpperCase();
        ZoneId exchangeZone = resolveExchangeZone(meta);

        JsonNode timestamps = result.get("timestamp");
        JsonNode quote = quoteNode(result);
        if (timestamps == null || !timestamps.isArray() || quote == null) {
            return new ParsedSeries(currency, List.of());
        }

        JsonNode opens = quote.get("open");
        JsonNode highs = quote.get("high");
        JsonNode lows = quote.get("low");
        JsonNode closes = quote.get("close");
        JsonNode volumes = quote.get("volume");

        List<DailyCandle> out = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            LocalDate tradeDate = Instant.ofEpochSecond(timestamps.get(i).asLong())
                    .atZone(exchangeZone)
                    .toLocalDate();

            BigDecimal open = decimalAt(opens, i);
            BigDecimal high = decimalAt(highs, i);
            BigDecimal low = decimalAt(lows, i);
            BigDecimal close = decimalAt(closes, i);

            if (open == null || high == null || low == null || close == null) {
                quarantineRepository.save(symbol, tradeDate, "missing_fields",
                        "{\"index\":" + i + ",\"reason\":\"null OHLC in Yahoo chart\"}", SOURCE_NAME, null);
                continue;
            }

            // Yahoo reports a null volume for instruments that genuinely have none
            // (indices). Zero is the conventional reading there, and volume plays no
            // part in valuation — unlike a null close, which is quarantined above.
            long volume = volumes == null || volumes.get(i) == null || volumes.get(i).isNull()
                    ? 0L
                    : volumes.get(i).asLong();

            out.add(new DailyCandle(tradeDate, open, high, low, close, volume));
        }

        out.sort(Comparator.comparing(DailyCandle::tradeDate));
        return new ParsedSeries(currency, out);
    }

    private JsonNode quoteNode(JsonNode result) {
        JsonNode indicators = result.get("indicators");
        if (indicators == null) return null;
        JsonNode quotes = indicators.get("quote");
        if (quotes == null || !quotes.isArray() || quotes.isEmpty()) return null;
        return quotes.get(0);
    }

    private ZoneId resolveExchangeZone(JsonNode meta) {
        if (meta != null && meta.get("exchangeTimezoneName") != null && !meta.get("exchangeTimezoneName").isNull()) {
            try {
                return ZoneId.of(meta.get("exchangeTimezoneName").asText());
            } catch (Exception ignored) {
                // Unrecognized zone id: fall through to the default below.
            }
        }
        return ZoneId.of("America/New_York");
    }

    /**
     * Normalised on the way in, so the number served equals the number stored.
     */
    private BigDecimal decimalAt(JsonNode array, int index) {
        if (array == null || !array.isArray() || index >= array.size()) return null;
        JsonNode value = array.get(index);
        if (value == null || value.isNull()) return null;
        return new BigDecimal(value.asText()).setScale(QUOTE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
