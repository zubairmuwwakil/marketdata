package com.zubairmuwwakil.marketdata.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.config.ExternalApiProperties;
import com.zubairmuwwakil.marketdata.config.ProviderProperties;
import com.zubairmuwwakil.marketdata.resilience.SimpleCircuitBreaker;
import com.zubairmuwwakil.marketdata.security.ProviderCredentials;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client for Questrade's REST API market data endpoints.
 *
 * <p><strong>Endpoints:</strong>
 * <ul>
 *   <li><code>/v1/symbols/search?prefix=...</code> to resolve symbol ID and currency</li>
 *   <li><code>/v1/markets/quotes/{symbolId}</code> for latest market close quotes</li>
 *   <li><code>/v1/markets/candles/{symbolId}?interval=OneDay</code> for daily candle history</li>
 * </ul>
 *
 * <p>Supports per-request OAuth bearer tokens supplied via {@code X-Provider-Key: QUESTRADE=<token>}
 * or {@code QUESTRADE=<token>@<serverUrl>}, as well as server-configured tokens.
 */
@Component
public class QuestradeClient {

    public record QuestradeSymbol(int symbolId, String symbol, String currency) {}

    private final ProviderProperties.Questrade config;
    private final ExternalApiProperties externalApiProperties;
    private final ObjectMapper objectMapper;
    private final SimpleCircuitBreaker circuitBreaker;
    private final Map<String, QuestradeSymbol> symbolCache = new ConcurrentHashMap<>();

    public QuestradeClient(ProviderProperties providerProperties,
                           ExternalApiProperties externalApiProperties,
                           ObjectMapper objectMapper) {
        this.config = providerProperties.getQuestrade();
        this.externalApiProperties = externalApiProperties;
        this.objectMapper = objectMapper;
        this.circuitBreaker = new SimpleCircuitBreaker(
                externalApiProperties.getCircuitBreaker().getFailureThreshold(),
                externalApiProperties.getCircuitBreaker().getOpenStateDuration(),
                externalApiProperties.getCircuitBreaker().getHalfOpenMaxCalls()
        );
    }

    /**
     * Resolves symbol metadata including Questrade symbolId and currency (CAD/USD).
     */
    public Optional<QuestradeSymbol> searchSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        QuestradeSymbol cached = symbolCache.get(normalized);
        if (cached != null) {
            return Optional.of(cached);
        }

        ResolvedAuth auth = resolveAuth();
        return executeWithResilience(() -> {
            String body = buildClient(auth.baseUrl()).get()
                    .uri(builder -> builder.path("/v1/symbols/search").queryParam("prefix", normalized).build())
                    .header("Authorization", "Bearer " + auth.token())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalServiceException("Questrade server error: HTTP " + res.getStatusCode(), true);
                    })
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new SymbolNotFoundException(symbol);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        boolean retryable = res.getStatusCode().value() == 429;
                        throw new ExternalServiceException("Questrade client error: HTTP " + res.getStatusCode(), retryable);
                    })
                    .body(String.class);

            Optional<QuestradeSymbol> result = parseSymbolSearchResult(body, normalized);
            result.ifPresent(s -> symbolCache.put(normalized, s));
            return result;
        });
    }

    /**
     * Latest market quote for a resolved symbol ID.
     */
    public Optional<JsonNode> quote(int symbolId) {
        ResolvedAuth auth = resolveAuth();
        return executeWithResilience(() -> {
            String body = buildClient(auth.baseUrl()).get()
                    .uri(builder -> builder.path("/v1/markets/quotes/{id}").build(symbolId))
                    .header("Authorization", "Bearer " + auth.token())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalServiceException("Questrade server error: HTTP " + res.getStatusCode(), true);
                    })
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new SymbolNotFoundException("symbolId:" + symbolId);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        boolean retryable = res.getStatusCode().value() == 429;
                        throw new ExternalServiceException("Questrade client error: HTTP " + res.getStatusCode(), retryable);
                    })
                    .body(String.class);

            return parseQuotesResult(body);
        });
    }

    /**
     * Daily historical candles for a resolved symbol ID.
     */
    public Optional<JsonNode> dailyCandles(int symbolId, LocalDate from, LocalDate to) {
        ResolvedAuth auth = resolveAuth();
        ZoneId ny = ZoneId.of("America/New_York");
        String startTime = from.atStartOfDay(ny).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String endTime = to.plusDays(1).atStartOfDay(ny).minusSeconds(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return executeWithResilience(() -> {
            String body = buildClient(auth.baseUrl()).get()
                    .uri(builder -> builder
                            .path("/v1/markets/candles/{id}")
                            .queryParam("startTime", startTime)
                            .queryParam("endTime", endTime)
                            .queryParam("interval", "OneDay")
                            .build(symbolId))
                    .header("Authorization", "Bearer " + auth.token())
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new ExternalServiceException("Questrade server error: HTTP " + res.getStatusCode(), true);
                    })
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new SymbolNotFoundException("symbolId:" + symbolId);
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        boolean retryable = res.getStatusCode().value() == 429;
                        throw new ExternalServiceException("Questrade client error: HTTP " + res.getStatusCode(), retryable);
                    })
                    .body(String.class);

            return parseCandlesResult(body);
        });
    }

    private Optional<QuestradeSymbol> parseSymbolSearchResult(String body, String targetSymbol) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode symbols = root.get("symbols");
            if (symbols == null || !symbols.isArray() || symbols.isEmpty()) {
                return Optional.empty();
            }
            for (JsonNode s : symbols) {
                String sym = s.has("symbol") ? s.get("symbol").asText() : "";
                if (sym.equalsIgnoreCase(targetSymbol)) {
                    int id = s.get("symbolId").asInt();
                    String currency = s.has("currency") ? s.get("currency").asText("USD").toUpperCase(Locale.ROOT) : "USD";
                    return Optional.of(new QuestradeSymbol(id, sym, currency));
                }
            }
            // If no exact match, fallback to first item
            JsonNode first = symbols.get(0);
            int id = first.get("symbolId").asInt();
            String sym = first.has("symbol") ? first.get("symbol").asText() : targetSymbol;
            String currency = first.has("currency") ? first.get("currency").asText("USD").toUpperCase(Locale.ROOT) : "USD";
            return Optional.of(new QuestradeSymbol(id, sym, currency));
        } catch (Exception ex) {
            throw new ExternalServiceException("Failed to parse Questrade symbol search response", true, ex);
        }
    }

    private Optional<JsonNode> parseQuotesResult(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode quotes = root.get("quotes");
            if (quotes == null || !quotes.isArray() || quotes.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(quotes.get(0));
        } catch (Exception ex) {
            throw new ExternalServiceException("Failed to parse Questrade quotes response", true, ex);
        }
    }

    private Optional<JsonNode> parseCandlesResult(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode candles = root.get("candles");
            if (candles == null || !candles.isArray() || candles.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(candles);
        } catch (Exception ex) {
            throw new ExternalServiceException("Failed to parse Questrade candles response", true, ex);
        }
    }

    private record ResolvedAuth(String token, String baseUrl) {}

    private ResolvedAuth resolveAuth() {
        Optional<String> userKey = ProviderCredentials.forProvider("QUESTRADE");
        if (userKey.isPresent() && !userKey.get().isBlank()) {
            String raw = userKey.get().trim();
            if (raw.contains("@")) {
                int at = raw.indexOf('@');
                String token = raw.substring(0, at).trim();
                String url = raw.substring(at + 1).trim();
                return new ResolvedAuth(token, url.isEmpty() ? config.getBaseUrl() : url);
            }
            return new ResolvedAuth(raw, config.getBaseUrl());
        }

        String appToken = config.getAccessToken();
        if (appToken != null && !appToken.isBlank()) {
            return new ResolvedAuth(appToken.trim(), config.getBaseUrl());
        }

        throw new IllegalStateException(
                "No Questrade credential available. Supply one per-request via "
                + ProviderCredentials.HEADER + " (e.g. QUESTRADE=your_token) or configure QUESTRADE_ACCESS_TOKEN.");
    }

    private RestClient buildClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(externalApiProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(externalApiProperties.getReadTimeout());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    private <T> Optional<T> executeWithResilience(Supplier<Optional<T>> call) {
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
        throw last == null ? new ExternalServiceException("Questrade call failed", true) : last;
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
