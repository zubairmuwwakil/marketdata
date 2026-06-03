package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.entity.WatchlistSymbol;
import com.zubairmuwwakil.marketdata.repository.WatchlistSymbolRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private static final int MAX_SYMBOLS = 25;
    private static final Pattern SYMBOL = Pattern.compile("^[A-Z0-9.\\-]{1,20}$");

    private final WatchlistSymbolRepository repo;

    public WatchlistController(WatchlistSymbolRepository repo) {
        this.repo = repo;
    }

    public record WatchlistRequest(@NotNull List<String> symbols) {}
    public record WatchlistResponse(List<String> symbols) {}

    @GetMapping
    public WatchlistResponse get() {
        var symbols = repo.findAllByActiveTrueOrderBySymbolAsc()
                .stream().map(WatchlistSymbol::getSymbol).toList();
        return new WatchlistResponse(symbols);
    }

    @PutMapping
    public ResponseEntity<?> set(@RequestBody WatchlistRequest req) {
        if (req.symbols() == null) return ResponseEntity.badRequest().body("symbols is required");

        List<String> cleaned = req.symbols().stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (cleaned.size() > MAX_SYMBOLS) {
            return ResponseEntity.badRequest().body("Max 25 symbols allowed.");
        }
        for (String s : cleaned) {
            if (!SYMBOL.matcher(s).matches()) {
                return ResponseEntity.badRequest().body("Invalid symbol: " + s);
            }
        }

        // Simplest “replace” semantics:
        // 1) deactivate all
        repo.findAll().forEach(w -> { w.setActive(false); repo.save(w); });

        // 2) upsert active symbols
        for (String sym : cleaned) {
            var existing = repo.findBySymbol(sym);
            if (existing.isPresent()) {
                existing.get().setActive(true);
                repo.save(existing.get());
            } else {
                repo.save(WatchlistSymbol.builder().symbol(sym).active(true).build());
            }
        }

        return ResponseEntity.ok(new WatchlistResponse(cleaned));
    }
}