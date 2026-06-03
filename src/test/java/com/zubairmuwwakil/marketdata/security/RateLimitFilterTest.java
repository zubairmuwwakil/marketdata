package com.zubairmuwwakil.marketdata.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.config.RateLimitProperties;
import com.zubairmuwwakil.marketdata.service.ingestion.QuotaService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCapacity(1);
        properties.setRefillTokens(1);
        properties.setRefillPeriod(Duration.ofHours(1));

        QuotaService quotaService = mock(QuotaService.class);
        when(quotaService.remainingToday()).thenReturn(QuotaService.DAILY_LIMIT);

        ApiProblemResponseWriter problemResponseWriter = new ApiProblemResponseWriter(new ObjectMapper());

        mockMvc = MockMvcBuilders.standaloneSetup(new ProtectedTestController())
                .addFilters(new RateLimitFilter(properties, quotaService, problemResponseWriter))
                .build();
    }

    @Test
    void secondRequestReturnsProblemResponseWhenRateLimitExceeded() throws Exception {
        mockMvc.perform(get("/api/v1/protected"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/protected"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail", Matchers.startsWith("Rate limit exceeded. Retry after ")));
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/v1/protected")
        ResponseEntity<Map<String, String>> get() {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
    }
}
