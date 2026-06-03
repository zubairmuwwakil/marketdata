package com.zubairmuwwakil.marketdata.security;

import com.zubairmuwwakil.marketdata.config.ApiKeyProperties;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApiKeyRegistry {

    public enum Source { CONFIG, GENERATED }

    public record KeyRecord(String key, String role, String label, Instant createdAt, Source source, Integer limit) {}

    public record KeyView(String keyId, String keyPrefix, String role, String label, Source source, Instant createdAt,
                          int used, int limit, int remaining, boolean mutable) {}

    private final Map<String, KeyRecord> keys = new ConcurrentHashMap<>();
    private final AppKeyQuotaService quotaService;

    public ApiKeyRegistry(ApiKeyProperties properties, AppKeyQuotaService quotaService) {
        this.quotaService = quotaService;
        if (properties != null && properties.getApiKeys() != null) {
            for (ApiKeyProperties.ApiKeyEntry entry : properties.getApiKeys()) {
                if (entry == null || entry.key() == null) continue;
                keys.put(entry.key(), new KeyRecord(entry.key(), entry.role(), "config", null, Source.CONFIG, null));
                quotaService.registerKey(entry.key(), null);
            }
        }
    }

    public Optional<KeyRecord> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(keys.get(key));
    }

    public List<KeyView> list() {
        List<KeyView> out = new ArrayList<>();
        for (KeyRecord rec : keys.values()) {
            var usage = quotaService.getUsage(rec.key());
            out.add(new KeyView(
                    fingerprint(rec.key()),
                    prefix(rec.key()),
                    rec.role(),
                    rec.label(),
                    rec.source(),
                    rec.createdAt(),
                    usage.used(),
                    usage.limit(),
                    usage.remaining(),
                    rec.source() == Source.GENERATED
            ));
        }
        out.sort(Comparator.comparing(KeyView::source).thenComparing(KeyView::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    public KeyRecord generate(String role, String label, Integer limit) {
        String key = "ml_" + UUID.randomUUID().toString().replace("-", "");
        KeyRecord rec = new KeyRecord(key, role, label, Instant.now(), Source.GENERATED, limit);
        keys.put(key, rec);
        quotaService.registerKey(key, limit);
        return rec;
    }

    public Optional<KeyRecord> rotate(String keyId) {
        String oldKey = findKeyById(keyId).orElse(null);
        if (oldKey == null) return Optional.empty();
        KeyRecord old = keys.get(oldKey);
        if (old == null || old.source() != Source.GENERATED) return Optional.empty();
        keys.remove(oldKey);
        return Optional.of(generate(old.role(), old.label(), old.limit()));
    }

    public boolean delete(String keyId) {
        String key = findKeyById(keyId).orElse(null);
        if (key == null) return false;
        KeyRecord rec = keys.get(key);
        if (rec == null || rec.source() != Source.GENERATED) return false;
        keys.remove(key);
        return true;
    }

    private Optional<String> findKeyById(String keyId) {
        if (keyId == null || keyId.isBlank()) return Optional.empty();
        return keys.keySet().stream().filter(k -> fingerprint(k).equals(keyId)).findFirst();
    }

    private String prefix(String key) {
        if (key == null) return "";
        int len = Math.min(6, key.length());
        return key.substring(0, len) + "…";
    }

    private String fingerprint(String apiKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(apiKey.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < digest.length; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "unknown";
        }
    }
}
