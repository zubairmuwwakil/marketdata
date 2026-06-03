package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.dto.IndicatorDto;
import com.zubairmuwwakil.marketdata.model.entity.IndicatorType;
import com.zubairmuwwakil.marketdata.service.indicator.IndicatorQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/indicators")
public class IndicatorController {

    private final IndicatorQueryService service;

    public IndicatorController(IndicatorQueryService service) {
        this.service = service;
    }

    // GET /api/v1/indicators/AAPL
    @GetMapping("/{symbol}")
    public ResponseEntity<List<IndicatorDto>> getAllIndicators(
            @PathVariable String symbol,
            @RequestParam(name = "after", required = false) LocalDate after,
            @RequestParam(name = "limit", defaultValue = "200") int limit
    ) {
        int bounded = Math.min(Math.max(limit, 1), 500);
        return ResponseEntity.ok(
            service.getAllForSymbol(symbol.toUpperCase(), after, bounded)
        );
    }

    // GET /api/v1/indicators/AAPL/RSI_14
    @GetMapping("/{symbol}/{type}")
    public ResponseEntity<List<IndicatorDto>> getByType(
            @PathVariable String symbol,
            @PathVariable IndicatorType type,
            @RequestParam(name = "after", required = false) LocalDate after,
            @RequestParam(name = "limit", defaultValue = "200") int limit
    ) {
        int bounded = Math.min(Math.max(limit, 1), 500);
        return ResponseEntity.ok(
            service.getByType(symbol.toUpperCase(), type, after, bounded)
        );
    }
}
