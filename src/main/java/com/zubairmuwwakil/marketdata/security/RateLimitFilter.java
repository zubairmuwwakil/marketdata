package com.zubairmuwwakil.marketdata.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import com.zubairmuwwakil.marketdata.config.RateLimitProperties;
import com.zubairmuwwakil.marketdata.service.ingestion.QuotaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final QuotaService quotaService;
    private final ApiProblemResponseWriter problemResponseWriter;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties,
                           QuotaService quotaService,
                           ApiProblemResponseWriter problemResponseWriter) {
        this.properties = properties;
        this.quotaService = quotaService;
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = buckets.computeIfAbsent(resolveKey(request), key -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        long limit = properties.getCapacity();
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000L));

        response.setHeader("X-Quota-Limit", String.valueOf(QuotaService.DAILY_LIMIT));
        response.setHeader("X-Quota-Remaining", String.valueOf(quotaService.remainingToday()));

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfter = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        problemResponseWriter.writeRateLimitExceeded(response, retryAfter);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.builder()
            .capacity(properties.getCapacity())
            .refillIntervally(properties.getRefillTokens(), properties.getRefillPeriod())
            .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveKey(HttpServletRequest request) {
        String apiKey = request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }
        return "ip:" + request.getRemoteAddr();
    }
}
