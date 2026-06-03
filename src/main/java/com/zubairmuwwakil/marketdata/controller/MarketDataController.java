package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.service.market.AdjustedPriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/market")
public class MarketDataController {

    private final AdjustedPriceService adjustedPriceService;

    public MarketDataController(AdjustedPriceService adjustedPriceService) {
        this.adjustedPriceService = adjustedPriceService;
    }

    @GetMapping("/adjusted")
    public List<AdjustedPriceService.AdjustedCandle> adjusted(
            @RequestParam String symbol,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return adjustedPriceService.getAdjustedCandles(symbol.toUpperCase(Locale.ROOT), from, to);
    }
}