package com.zubairmuwwakil.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "marketdata")
public record MarketDataProperties(
        AlphaVantage alphavantage
) {
    public record AlphaVantage(
            String baseUrl,
            String apiKey,
            String outputsize
    ) {}
}