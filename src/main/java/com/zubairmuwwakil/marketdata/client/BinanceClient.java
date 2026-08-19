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
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Thin client over Binance's public market data endpoints.
 *
 * <p><strong>Endpoints:</strong>
 * <ul>
 *   <li><code>/api/v3/ticker/24hr?symbol=...</code> for latest 24h stats / daily close.</li>
 *   <li><code>/api/v3/klines?symbol=...&amp;interval=1d</code> for daily OHLCV series.</li>
 * </ul>
 *
 * <p>Public endpoints require no API keys, answer HTTP 200, and quote in USDT or
 * the pair's quote asset. Unknown/delisted symbols answer HTTP 400 with code
 * {@code -1121}, which is caught and mapped to {@link SymbolNotFoundException}
 * so circuit breakers never trip on invalid tickers.
 */
@Component
public class BinanceClient {

    private static final Set<String> KNOWN_QUOTE_ASSETS = Set.of(
            "USDT", "USDC", "BUSD", "FDUSD", "TUSD", "USD", "EUR", "GBP", "CAD", "AUD", "BTC", "ETH", "BNB"
    );

    private final RestClient restClient;
    private final ProviderProperties.Binance config;
    private final ObjectMapper objectMapper;
    private final SimpleCircuitBreaker circuitBreaker;

    public BinanceClient(ProviderProperties providerProperties,
                         ExternalApiProperties externalApiProperties,
                         ObjectMapper objectMapper) {
        this.config = providerProperties.getBinance();
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(externalApiProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(externalApiProperties.getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .build();

        this.circuitBreaker = new SimpleCircuitBreaker(
                externalApiProperties.getCircuitBreaker().getFailureThreshold(),
                externalApiProperties.getCircuitBreaker().getOpenStateDuration(),
                externalApiProperties.getCircuitBreaker().getHalfOpenMaxCalls()
        );
    }

    /**
     * 24h rolling ticker statistics including latest close price and volume.
     */
    public Optional<JsonNode> ticker24hr(String symbol) {
        String pair = normalizePair(symbol);
        return executeWithResilience(() -> {
            String body = restClient.get()
                    .uri(builder -> builder.path("/api/v3/ticker/24hr").queryParam("symbol", pair).build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalServiceException("Binance server error: HTTP " + res.getStatusCode(), true);
                    })
                    .onStatus(status -> status.value() == 400, (req, res) -> {
                        // Binance returns 400 for invalid symbol {"code":-1121,"msg":"Invalid symbol."}
                        throw new SymbolNotFoundException(symbol);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        boolean retryable = res.getStatusCode().value() == 429 || res.getStatusCode().value() == 418;
                        throw new ExternalServiceException("Binance client error: HTTP " + res.getStatusCode(), retryable);
                    })
                    .body(String.class);
            return parseJson(body, symbol);
        });
    }

    /**
     * Daily klines (candles) for a date range.
     */
    public Optional<JsonNode> dailyKlines(String symbol, LocalDate from, LocalDate to) {
        String pair = normalizePair(symbol);
        long startTime = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long endTime = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

        return executeWithResilience(() -> {
            String body = restClient.get()
                    .uri(builder -> builder
                            .path("/api/v3/klines")
                            .queryParam("symbol", pair)
                            .queryParam("interval", "1d")
                            .queryParam("startTime", startTime)
                            .queryParam("endTime", endTime)
                            .queryParam("limit", 1000)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalServiceException("Binance server error: HTTP " + res.getStatusCode(), true);
                    })
                    .onStatus(status -> status.value() == 400, (req, res) -> {
                        throw new SymbolNotFoundException(symbol);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        boolean retryable = res.getStatusCode().value() == 429 || res.getStatusCode().value() == 418;
                        throw new ExternalServiceException("Binance client error: HTTP " + res.getStatusCode(), retryable);
                    })
                    .body(String.class);
            return parseJson(body, symbol);
        });
    }

    /**
     * Normalizes an input symbol (e.g. "BTC", "BTC-USD", "BTC/USDT", "BTCUSDT")
     * into a standard Binance trading pair (e.g. "BTCUSDT").
     */
    public static String normalizePair(String symbol) {
        if (symbol == null) return "";
        String clean = symbol.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("/", "").replace("_", "");
        if (clean.isEmpty()) return "";

        for (String quote : KNOWN_QUOTE_ASSETS) {
            if (clean.endsWith(quote) && clean.length() > quote.length()) {
                if (quote.equals("USD")) {
                    return clean.substring(0, clean.length() - 3) + "USDT";
                }
                return clean;
            }
        }
        return clean + "USDT";
    }

    /**
     * Resolves the quote currency for an input symbol.
     */
    public static String resolveCurrency(String symbol) {
        String pair = normalizePair(symbol);
        for (String quote : KNOWN_QUOTE_ASSETS) {
            if (pair.endsWith(quote) && pair.length() > quote.length()) {
                return quote;
            }
        }
        return "USDT";
    }

    private Optional<JsonNode> parseJson(String body, String symbol) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.isNull() || (root.isArray() && root.isEmpty())) {
                return Optional.empty();
            }
            if (root.has("code") && root.get("code").asInt() != 0) {
                return Optional.empty();
            }
            return Optional.of(root);
        } catch (Exception ex) {
            throw new ExternalServiceException("Failed to parse Binance JSON for " + symbol, true, ex);
        }
    }

    private Optional<JsonNode> executeWithResilience(Supplier<Optional<JsonNode>> call) {
        return circuitBreaker.execute(() -> {
            try {
                return retryWithBackoff(call);
            } catch (SymbolNotFoundException ex) {
                return Optional.empty();
            }
        });
    }

    private <T> T retryWithBackoff(Supplier<T> call) {
        RuntimeException last = null;
        int maxAttempts = 3;
        Duration backoff = Duration.ofMillis(300);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (SymbolNotFoundException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                last = ex;
                if (!isRetryable(ex) || attempt == maxAttempts) {
                    throw ex;
                }
                sleep(backoff.multipliedBy(attempt));
            }
        }
        throw last == null ? new ExternalServiceException("Binance call failed", true) : last;
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
