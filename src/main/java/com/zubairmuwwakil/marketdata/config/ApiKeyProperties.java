package com.zubairmuwwakil.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ConfigurationProperties(prefix = "marketdata.security")
public class ApiKeyProperties {

    private List<ApiKeyEntry> apiKeys = new ArrayList<>();

    public List<ApiKeyEntry> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<ApiKeyEntry> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public Optional<ApiKeyEntry> findByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return apiKeys.stream()
                .filter(k -> key.equals(k.key()))
                .findFirst();
    }

    public record ApiKeyEntry(String key, String role) {}
}