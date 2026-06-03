package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.security.ApiKeyRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/keys")
public class ApiKeyAdminController {

    public record CreateKeyRequest(String role, String label, Integer limit) {}

    private final ApiKeyRegistry registry;

    public ApiKeyAdminController(ApiKeyRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<ApiKeyRegistry.KeyView> list() {
        return registry.list();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody(required = false) CreateKeyRequest request) {
        String role = request == null || request.role() == null ? "USER" : request.role();
        String label = request == null ? "generated" : request.label();
        var rec = registry.generate(role, label, request == null ? null : request.limit());
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("key", rec.key());
        body.put("role", rec.role());
        body.put("label", rec.label());
        body.put("limit", rec.limit() == null ? 5000 : rec.limit());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{keyId}/rotate")
    public ResponseEntity<Map<String, Object>> rotate(@PathVariable String keyId) {
        return registry.rotate(keyId)
            .map(rec -> {
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("key", rec.key());
                body.put("role", rec.role());
                body.put("label", rec.label());
                body.put("limit", rec.limit() == null ? 5000 : rec.limit());
                return ResponseEntity.ok(body);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{keyId}")
    public ResponseEntity<Void> delete(@PathVariable String keyId) {
        boolean removed = registry.delete(keyId);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
