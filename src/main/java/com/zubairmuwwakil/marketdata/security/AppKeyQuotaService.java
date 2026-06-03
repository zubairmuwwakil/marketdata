package com.zubairmuwwakil.marketdata.security;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppKeyQuotaService {

    public record Usage(LocalDate date, int used, int limit, int remaining) {}

    private static final int DEFAULT_LIMIT = 5000;

    private final Map<String, Integer> limits = new ConcurrentHashMap<>();
    private final Map<String, DailyUsage> usage = new ConcurrentHashMap<>();

    public void registerKey(String key, Integer limit) {
        if (key == null || key.isBlank()) return;
        limits.putIfAbsent(key, limit == null ? DEFAULT_LIMIT : limit);
    }

    public Usage consume(String key) {
        if (key == null || key.isBlank()) {
            return new Usage(LocalDate.now(), 0, 0, 0);
        }
        limits.putIfAbsent(key, DEFAULT_LIMIT);
        DailyUsage day = usage.compute(key, (k, existing) -> {
            LocalDate today = LocalDate.now();
            if (existing == null || !existing.date.equals(today)) {
                return new DailyUsage(today, 1);
            }
            return new DailyUsage(existing.date, existing.used + 1);
        });
        int limit = limits.getOrDefault(key, DEFAULT_LIMIT);
        int remaining = Math.max(0, limit - day.used);
        if (day.used > limit) {
            throw new IllegalStateException("MarketLens API quota exceeded");
        }
        return new Usage(day.date, day.used, limit, remaining);
    }

    public Usage getUsage(String key) {
        limits.putIfAbsent(key, DEFAULT_LIMIT);
        DailyUsage day = usage.get(key);
        LocalDate today = LocalDate.now();
        if (day == null || !day.date.equals(today)) {
            return new Usage(today, 0, limits.get(key), limits.get(key));
        }
        int limit = limits.getOrDefault(key, DEFAULT_LIMIT);
        return new Usage(day.date, day.used, limit, Math.max(0, limit - day.used));
    }

    public void setLimit(String key, int limit) {
        if (key == null || key.isBlank()) return;
        limits.put(key, limit);
    }

    private record DailyUsage(LocalDate date, int used) {}
}
