package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.service.ingestion.QuotaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> quota() {
        int remaining = quotaService.remainingToday();
        return ResponseEntity.ok(Map.of(
                "provider", QuotaService.PROVIDER,
                "remaining", remaining,
                "limit", QuotaService.DAILY_LIMIT
        ));
    }
}
