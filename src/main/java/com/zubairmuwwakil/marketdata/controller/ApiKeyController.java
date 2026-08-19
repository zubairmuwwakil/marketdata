package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.client.AlphaVantageClient;
import com.zubairmuwwakil.marketdata.service.ingestion.ProviderKeyStore;
import com.zubairmuwwakil.marketdata.service.ingestion.QuotaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class ApiKeyController {

    private final ProviderKeyStore store;
    private final QuotaService quotaService;
    private final AlphaVantageClient alphaVantageClient;

    public ApiKeyController(ProviderKeyStore store, QuotaService quotaService, AlphaVantageClient alphaVantageClient) {
        this.store = store;
        this.quotaService = quotaService;
        this.alphaVantageClient = alphaVantageClient;
    }

    @PostMapping("/api-key")
    public ResponseEntity<Map<String, Object>> setKey(@RequestBody Map<String, String> body) {
        String key = body.get("apiKey");
        var result = alphaVantageClient.validateKey(key);

        if (result.status() == AlphaVantageClient.KeyValidationStatus.INVALID) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "API key invalid: " + result.message(),
                    "status", result.status().name()
            ));
        }

        if (result.status() == AlphaVantageClient.KeyValidationStatus.QUOTA_EXHAUSTED) {
            return ResponseEntity.status(429).body(Map.of(
                    "message", "API key quota exhausted: " + result.message(),
                    "status", result.status().name()
            ));
        }

        store.set(key);
        int remaining = quotaService.resetToday().getCallsLimit();
        return ResponseEntity.ok(Map.of(
                "message", result.message() == null || result.message().isBlank()
                        ? "API key validated and saved"
                        : result.message(),
                "status", result.status().name(),
                "remainingQuotaReset", true,
                "remaining", remaining,
                "limit", QuotaService.DAILY_LIMIT
        ));
    }
}
