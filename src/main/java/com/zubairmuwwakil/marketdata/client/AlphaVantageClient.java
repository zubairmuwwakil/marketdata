package com.zubairmuwwakil.marketdata.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.config.ExternalApiProperties;
import com.zubairmuwwakil.marketdata.config.MarketDataProperties;
import com.zubairmuwwakil.marketdata.resilience.SimpleCircuitBreaker;
import com.zubairmuwwakil.marketdata.security.ProviderCredentials;
import com.zubairmuwwakil.marketdata.service.ingestion.ProviderKeyStore;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

@Component
public class AlphaVantageClient {

    public static final String SOURCE_NAME = "ALPHAVANTAGE";

    private final RestClient restClient;
    private final MarketDataProperties props;
    private final ObjectMapper objectMapper;
    private final ProviderKeyStore providerKeyStore;
    private final ExternalApiProperties externalApiProperties;
    private final SimpleCircuitBreaker circuitBreaker;
    private final boolean demoMode;

    public enum KeyValidationStatus {
        VALID,
        QUOTA_EXHAUSTED,
        INVALID
    }

    public record KeyValidationResult(KeyValidationStatus status, String message) {}

    public AlphaVantageClient(MarketDataProperties props,
                              ObjectMapper objectMapper,
                              ProviderKeyStore providerKeyStore,
                              ExternalApiProperties externalApiProperties,
                              Environment environment) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.providerKeyStore = providerKeyStore;
        this.externalApiProperties = externalApiProperties;
        this.demoMode = environment.matchesProfiles("demo");

        var av = props.alphavantage();
        if (av == null || av.baseUrl() == null || av.baseUrl().isBlank()) {
            throw new IllegalStateException("Missing config: marketdata.alphavantage.base-url");
        }

        if (av.apiKey() != null && !av.apiKey().isBlank()) {
            this.providerKeyStore.set(av.apiKey());
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(externalApiProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(externalApiProperties.getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(av.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.circuitBreaker = new SimpleCircuitBreaker(
                externalApiProperties.getCircuitBreaker().getFailureThreshold(),
                externalApiProperties.getCircuitBreaker().getOpenStateDuration(),
                externalApiProperties.getCircuitBreaker().getHalfOpenMaxCalls()
        );
    }

    public JsonNode timeSeriesDaily(String symbol) {
        if (demoMode) {
            throw new IllegalStateException("Demo profile uses seeded market data and does not call Alpha Vantage.");
        }
        // BYOK: the caller's own key wins over the app key, so their data can be
        // fetched under their own licence and quota (ADR 0003 section 8).
        ProviderKeyStore.ResolvedKey resolved = providerKeyStore.resolve(SOURCE_NAME);
        if (!resolved.isPresent()) {
            throw new IllegalStateException(
                    "No Alpha Vantage credential available. Supply one per-request via the "
                    + ProviderCredentials.HEADER + " header, or set ALPHAVANTAGE_API_KEY "
                    + "(the dashboard key is an in-memory session override and does not "
                    + "survive a restart).");
        }
        String key = resolved.key();

        return executeWithResilience(() -> {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", "TIME_SERIES_DAILY")
                            .queryParam("symbol", symbol)
                            .queryParam("outputsize", props.alphavantage().outputsize())
                            .queryParam("apikey", key)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalServiceException("Alpha Vantage server error: HTTP " + res.getStatusCode(), true);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        boolean retryable = res.getStatusCode().value() == 429;
                        throw new ExternalServiceException("Alpha Vantage client error: HTTP " + res.getStatusCode(), retryable);
                    })
                    .body(String.class);
            try {
                JsonNode root = objectMapper.readTree(body);
                if (root.has("Note")) {
                    throw new ExternalServiceException("Alpha Vantage throttled request", true);
                }
                if (root.has("Error Message")) {
                    throw new ExternalServiceException(root.get("Error Message").asText(), false);
                }
                return root;
            } catch (ExternalServiceException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new ExternalServiceException("Failed to parse Alpha Vantage JSON", true, ex);
            }
        });
    }

    /**
     * Makes a cheap Alpha Vantage call to verify the given key is usable.
     * This consumes one external request/quota on Alpha Vantage.
     */
    public KeyValidationResult validateKey(String key) {
        if (demoMode) {
            return new KeyValidationResult(
                    KeyValidationStatus.VALID,
                    "Demo mode is active. External provider validation was skipped."
            );
        }
        if (key == null || key.isBlank()) {
            return new KeyValidationResult(KeyValidationStatus.INVALID, "API key is blank");
        }

        String body = executeWithResilience(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "GLOBAL_QUOTE")
                        .queryParam("symbol", "IBM")
                        .queryParam("apikey", key.trim())
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new ExternalServiceException("Alpha Vantage server error: HTTP " + res.getStatusCode(), true);
                })
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    boolean retryable = res.getStatusCode().value() == 429;
                    throw new ExternalServiceException("Alpha Vantage client error: HTTP " + res.getStatusCode(), retryable);
                })
                .body(String.class));

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("Note")) {
                return new KeyValidationResult(KeyValidationStatus.QUOTA_EXHAUSTED, root.get("Note").asText());
            }
            if (root.has("Error Message")) {
                return new KeyValidationResult(KeyValidationStatus.INVALID, root.get("Error Message").asText());
            }
            JsonNode quote = root.get("Global Quote");
            if (quote != null && quote.has("05. price")) {
                return new KeyValidationResult(KeyValidationStatus.VALID, "Key is valid");
            }
            return new KeyValidationResult(KeyValidationStatus.INVALID, "Unexpected Alpha Vantage response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Alpha Vantage JSON: " + e.getMessage(), e);
        }
    }

    private <T> T executeWithResilience(Supplier<T> call) {
        return circuitBreaker.execute(() -> retryWithBackoff(call));
    }

    private <T> T retryWithBackoff(Supplier<T> call) {
        int maxAttempts = Math.max(1, externalApiProperties.getRetry().getMaxAttempts());
        Duration backoff = externalApiProperties.getRetry().getBackoff();
        if (backoff == null) {
            backoff = Duration.ZERO;
        }
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException ex) {
                last = ex;
                if (!isRetryable(ex) || attempt == maxAttempts) {
                    throw ex;
                }
                sleep(backoff.multipliedBy(attempt));
            }
        }
        throw last == null ? new ExternalServiceException("Alpha Vantage call failed", true) : last;
    }

    private boolean isRetryable(RuntimeException ex) {
        if (ex instanceof ExternalServiceException serviceEx) {
            return serviceEx.isRetryable();
        }
        return ex instanceof RestClientException;
    }

    private void sleep(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
