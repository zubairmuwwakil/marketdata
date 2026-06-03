package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.entity.PriceCandle;
import com.zubairmuwwakil.marketdata.repository.PriceCandleRepository;
import com.zubairmuwwakil.marketdata.repository.WatchlistSymbolRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/market")
public class MarketSummaryController {

    public record SummaryRow(
            String symbol,
            String tradeDate,
            String open,
            String high,
            String low,
            String close,
            long volume
    ) {}

    private final WatchlistSymbolRepository watchlistRepo;
    private final PriceCandleRepository candleRepo;

    public MarketSummaryController(WatchlistSymbolRepository watchlistRepo,
                                   PriceCandleRepository candleRepo) {
        this.watchlistRepo = watchlistRepo;
        this.candleRepo = candleRepo;
    }

    @GetMapping("/summary")
    public ResponseEntity<List<SummaryRow>> summary(
            @RequestParam(name = "active", defaultValue = "true") boolean active
    ) {
        List<String> symbols;
        if (active) {
            symbols = watchlistRepo.findAllByActiveTrueOrderBySymbolAsc()
                    .stream()
                    .map(w -> w.getSymbol().trim().toUpperCase(Locale.ROOT))
                    .toList();
        } else {
            var activeSymbols = watchlistRepo.findAllByActiveTrueOrderBySymbolAsc()
                    .stream()
                    .map(w -> w.getSymbol().trim().toUpperCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            symbols = candleRepo.findDistinctSymbols().stream()
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .filter(s -> !activeSymbols.contains(s))
                    .toList();
        }

        List<SummaryRow> rows = symbols.stream()
                .map(sym -> candleRepo.findTop1BySymbolOrderByTradeDateDesc(sym)
                        .map(c -> toRow(sym, c))
                        .orElse(null))
                .filter(r -> r != null)
                .collect(Collectors.toList());

        return ResponseEntity.ok(rows);
    }

    private SummaryRow toRow(String sym, PriceCandle c) {
        return new SummaryRow(
                sym,
                c.getTradeDate().toString(),
                c.getOpen().toPlainString(),
                c.getHigh().toPlainString(),
                c.getLow().toPlainString(),
                c.getClose().toPlainString(),
                c.getVolume()
        );
    }
}
