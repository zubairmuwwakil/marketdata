package com.zubairmuwwakil.marketdata.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeUnauthorizedInvalidApiKey(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Missing or invalid API key.");
    }

    public void writeQuotaExceeded(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.TOO_MANY_REQUESTS, "Quota Exceeded", "MarketLens API key quota exceeded.");
    }

    public void writeRateLimitExceeded(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        write(
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                "Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds."
        );
    }

    private void write(HttpServletResponse response,
                       HttpStatus status,
                       String title,
                       String detail) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
