package com.zubairmuwwakil.marketdata.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.zubairmuwwakil.marketdata.client.AlphaVantageClient;
import com.zubairmuwwakil.marketdata.model.AssetClass;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.dto.QuotedCandle;
import com.zubairmuwwakil.marketdata.repository.IngestionQuarantineRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Profile("!demo")
public class AlphaVantageDailyProvider implements MarketDataProvider, LatestQuoteProvider {

    private final AlphaVantageClient client;
    private final IngestionQuarantineRepository quarantineRepository;

    public AlphaVantageDailyProvider(AlphaVantageClient client,
                                     IngestionQuarantineRepository quarantineRepository) {
        this.client = client;
        this.quarantineRepository = quarantineRepository;
    }

    @Override
    public List<DailyCandle> fetchDailyCandles(String symbol, LocalDate from, LocalDate to) {
        JsonNode root = client.timeSeriesDaily(symbol);

        JsonNode series = root.get("Time Series (Daily)");
        if (series == null) {
            return List.of();
        }

        List<DailyCandle> out = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> it = series.properties().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            LocalDate date;
            try {
                date = LocalDate.parse(entry.getKey());
            } catch (Exception ex) {
                JsonNode payload = entry.getValue();
                quarantineRepository.save(symbol, null, "parse_error:date", payload == null ? "{}" : payload.toString(), sourceName(), null);
                continue;
            }

            if (date.isBefore(from) || date.isAfter(to)) continue;

            JsonNode v = entry.getValue();
            if (v == null ||
                    v.get("1. open") == null ||
                    v.get("2. high") == null ||
                    v.get("3. low") == null ||
                    v.get("4. close") == null ||
                    v.get("5. volume") == null ||
                    v.get("4. close").isNull()) {
                quarantineRepository.save(symbol, date, "missing_fields", v == null ? "{}" : v.toString(), sourceName(), null);
                continue;
            }
            try {
                out.add(new DailyCandle(
                        date,
                        new BigDecimal(v.get("1. open").asText()),
                        new BigDecimal(v.get("2. high").asText()),
                        new BigDecimal(v.get("3. low").asText()),
                        new BigDecimal(v.get("4. close").asText()),
                        v.get("5. volume").asLong()
                ));
            } catch (Exception ex) {
                quarantineRepository.save(symbol, date, "parse_error:values", v.toString(), sourceName(), null);
            }
        }

        out.sort(Comparator.comparing(DailyCandle::tradeDate));
        return out;
    }

    @Override
    public Optional<QuotedCandle> fetchLatestClose(String symbol) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        List<DailyCandle> candles = fetchDailyCandles(symbol, from, to);
        if (candles.isEmpty()) {
            return Optional.empty();
        }
        DailyCandle latest = candles.get(candles.size() - 1);
        return Optional.of(new QuotedCandle(symbol, latest, priceCurrency(symbol), sourceName()));
    }

    @Override
    public String priceCurrency(String symbol) {
        return "USD";
    }

    @Override
    public Set<AssetClass> supportedAssetClasses() {
        return Set.of(AssetClass.EQUITY);
    }

    @Override
    public String sourceName() {
        return "ALPHAVANTAGE";
    }
}
