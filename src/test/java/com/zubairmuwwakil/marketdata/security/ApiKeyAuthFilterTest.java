package com.zubairmuwwakil.marketdata.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.config.ApiKeyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiKeyAuthFilterTest {

    private MockMvc mockMvc;
    private AppKeyQuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new AppKeyQuotaService();
        mockMvc = newMockMvc(new ApiKeyProperties(), quotaService);
    }

    @Test
    void missingApiKeyReturnsUnauthorizedProblemResponse() throws Exception {
        assertUnauthorized(get("/api/v1/protected"));
    }

    @Test
    void blankApiKeyReturnsUnauthorizedProblemResponse() throws Exception {
        assertUnauthorized(get("/api/v1/protected").header(ApiKeyAuthFilter.API_KEY_HEADER, "   "));
    }

    @Test
    void invalidApiKeyReturnsUnauthorizedProblemResponse() throws Exception {
        assertUnauthorized(get("/api/v1/protected").header(ApiKeyAuthFilter.API_KEY_HEADER, "bad-key"));
    }

    @Test
    void quotaExceededReturnsProblemResponse() throws Exception {
        ApiKeyProperties properties = new ApiKeyProperties();
        properties.setApiKeys(List.of(new ApiKeyProperties.ApiKeyEntry("valid-key", "USER")));
        quotaService = new AppKeyQuotaService();
        quotaService.setLimit("valid-key", 0);
        mockMvc = newMockMvc(properties, quotaService);

        mockMvc.perform(get("/api/v1/protected").header(ApiKeyAuthFilter.API_KEY_HEADER, "valid-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Quota Exceeded"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("MarketLens API key quota exceeded."));
    }

    private MockMvc newMockMvc(ApiKeyProperties properties, AppKeyQuotaService quotaService) {
        ApiProblemResponseWriter problemResponseWriter = new ApiProblemResponseWriter(new ObjectMapper());
        ApiAuthenticationEntryPoint authenticationEntryPoint = new ApiAuthenticationEntryPoint(problemResponseWriter);
        ApiKeyRegistry apiKeyRegistry = new ApiKeyRegistry(properties, quotaService);
        ApiKeyService apiKeyService = new ApiKeyService(apiKeyRegistry);

        return MockMvcBuilders.standaloneSetup(new ProtectedTestController())
                .addFilters(new ApiKeyAuthFilter(
                        apiKeyService,
                        quotaService,
                        authenticationEntryPoint,
                        problemResponseWriter
                ))
                .build();
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        mockMvc.perform(requestBuilder)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Missing or invalid API key."));
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/v1/protected")
        ResponseEntity<Map<String, String>> get() {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
    }
}
