package com.zubairmuwwakil.marketdata.security;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ApiKeyService {

    private final ApiKeyRegistry registry;

    public ApiKeyService(ApiKeyRegistry registry) {
        this.registry = registry;
    }

    public Optional<ApiKeyPrincipal> authenticate(String apiKey) {
        return registry.findByKey(apiKey)
                .map(entry -> new ApiKeyPrincipal(apiKey, entry.role()));
    }
}