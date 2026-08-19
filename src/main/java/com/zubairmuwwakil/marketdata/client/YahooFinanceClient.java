package com.zubairmuwwakil.marketdata.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.config.ExternalApiProperties;
import com.zubairmuwwakil.marketdata.config.ProviderProperties;
import com.zubairmuwwakil.marketdata.resilience.SimpleCircuitBreaker;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thin client over Yahoo Finance's chart endpoint, for daily closes only.
 *
 * <p><strong>Daily closes, never real-time</strong> (honesty invariant A6). The
 * response carries an intraday {@code regularMarketPrice}; this client
 * deliberately does not read it, because MarketLens does not serve intraday
 * pricing and quietly mixing in a live tick would make "as of the last close" a
 * lie.
 *
 * <p><strong>Endpoint choice, probed 2026-08-18:</strong> {@code /v8/finance/chart}
 * answers HTTP 200 with an ordinary User-Agent and needs no cookie/crumb dance,
 * so a plain Java client is sufficient — no browser emulation, no Python sidecar.
 * The multi-symbol {@code /v7/finance/quote} endpoint returns HTTP 401, so
 * <em>there is no working batch endpoint</em> and callers must fan out one symbol
 * at a time. An unknown or delisted symbol answers HTTP 404 with a JSON error
 * body, which is cleanly separable from throttling.
 *
 * <p>Accepted risk, recorded not hidden (E4): Yahoo's terms do not sanction this
 * access, and these endpoints break periodically — the 2023 cookie/crumb change
 * broke every wrapper for weeks. The provider registry is the mitigation: when it
 * breaks, another provider is a config change plus one class.
 */
@Component
public class YahooFinanceClient {

    private final RestClient restClient;
    private final ProviderProperties.Yahoo config;
    private final ObjectMapper objectMapper;
    private final SimpleCircuitBreaker circuitBreaker;

    public YahooFinanceClient(ProviderProperties providerProperties,
                              ExternalApiProperties externalApiProperties,
                              ObjectMapper objectMapper) {
        this.config = providerProperties.getYahoo();
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(externalApiProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(externalApiProperties.getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", config.getUserAgent())
                .defaultHeader("Accept", "application/json")
                .build();

        this.circuitBreaker = new SimpleCircuitBreaker(
                externalApiProperties.getCircuitBreaker().getFailureThreshold(),
                externalApiProperties.getCircuitBreaker().getOpenStateDuration(),
                externalApiProperties.getCircuitBreaker().getHalfOpenMaxCalls()
        );
    }

    /**
     * Daily candles for one symbol over a date range.
     *
     * @return the {@code chart.result[0]} node, or empty when Yahoo has no such
     *         symbol. Empty means "this symbol does not exist" — a permanent
     *         answer the caller should not retry. Transport failures throw
     *         {@link ExternalServiceException} instead, carrying whether a retry
     *         could plausibly help.
     */
    public Optional<JsonNode> dailyChart(String symbol, LocalDate from, LocalDate to) {
        // Yahoo's window is [period1, period2). Pad the end by a day so the most
        // recent session is inside the range rather than exactly on the boundary.
        long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        return executeWithResilience(() -> {
            String body = restClient.get()
                    .uri(builder -> builder
                            .path("/v8/finance/chart/{symbol}")
                            .queryParam("interval", "1d")
                            .queryParam("period1", period1)
                            .queryParam("period2", period2)
                            .build(symbol))
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalServiceException("Yahoo server error: HTTP " + res.getStatusCode(), true);
                    })
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        // Unknown or delisted. Permanent, and NOT an outage — caught
                        // below so it never reaches the circuit breaker, which counts
                        // every RuntimeException as a failure. A handful of typo'd
                        // tickers must not trip the breaker and starve real symbols.
                        throw new SymbolNotFoundException(symbol);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        // 429 is Yahoo throttling us; everything else is our own bad request.
                        boolean retryable = res.getStatusCode().value() == 429;
                        throw new ExternalServiceException("Yahoo client error: HTTP " + res.getStatusCode(), retryable);
                    })
                    .body(String.class);
            return parseChartResult(body, symbol);
        });
    }

    private Optional<JsonNode> parseChartResult(String body, String symbol) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new ExternalServiceException("Failed to parse Yahoo JSON for " + symbol, true, ex);
        }
        JsonNode chart = root.get("chart");
        if (chart == null) {
            throw new ExternalServiceException("Yahoo response had no chart node for " + symbol, true);
        }
        JsonNode error = chart.get("error");
        if (error != null && !error.isNull()) {
            // Yahoo also reports unknown symbols in-band with HTTP 200.
            return Optional.empty();
        }
        JsonNode results = chart.get("result");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }

    /**
     * Runs the call under retry + circuit breaker, but converts "no such symbol"
     * into an empty result <em>before</em> the breaker sees it. The breaker
     * treats every escaping RuntimeException as a provider failure, so letting
     * a 404 through would let a few mistyped tickers open the circuit and block
     * symbols that are perfectly fine.
     */
    private Optional<JsonNode> executeWithResilience(Supplier<Optional<JsonNode>> call) {
        return circuitBreaker.execute(() -> {
            try {
                return retryWithBackoff(call);
            } catch (SymbolNotFoundException ex) {
                return Optional.<JsonNode>empty();
            }
        });
    }

    private <T> T retryWithBackoff(Supplier<T> call) {
        RuntimeException last = null;
        int maxAttempts = 3;
        Duration backoff = Duration.ofMillis(400);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (SymbolNotFoundException ex) {
                // Permanent by definition; retrying spends budget to learn nothing.
                throw ex;
            } catch (RuntimeException ex) {
                last = ex;
                if (!isRetryable(ex) || attempt == maxAttempts) {
                    throw ex;
                }
                sleep(backoff.multipliedBy(attempt));
            }
        }
        throw last == null ? new ExternalServiceException("Yahoo call failed", true) : last;
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
