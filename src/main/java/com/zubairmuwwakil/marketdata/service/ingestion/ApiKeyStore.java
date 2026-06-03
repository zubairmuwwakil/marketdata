package com.zubairmuwwakil.marketdata.service.ingestion;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class ApiKeyStore {
    private final AtomicReference<String> keyRef = new AtomicReference<>("");

    public void set(String key) {
        keyRef.set(key == null ? "" : key.trim());
    }

    public String get() {
        return keyRef.get();
    }
}
