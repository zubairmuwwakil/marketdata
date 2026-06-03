package com.zubairmuwwakil.marketdata.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoProfileIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemoDatasetFactory datasetFactory;

    @Test
    void demoConfigEndpointIsPublicAndPopulated() throws Exception {
        mockMvc.perform(get("/api/v1/demo/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.defaultApiKey").isNotEmpty())
                .andExpect(jsonPath("$.featuredSymbol").value(datasetFactory.dataset().featuredSymbol()));
    }

    @Test
    void seededMarketSummaryLoadsForActiveSymbols() throws Exception {
        mockMvc.perform(get("/api/v1/market/summary?active=true")
                        .header("X-API-Key", "change-me-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").isNotEmpty())
                .andExpect(jsonPath("$[0].tradeDate").isNotEmpty())
                .andExpect(jsonPath("$[0].close").isNotEmpty());
    }

    @Test
    void seededIndicatorsLoadWithoutCursorParameter() throws Exception {
        mockMvc.perform(get("/api/v1/indicators/" + datasetFactory.dataset().featuredSymbol())
                        .header("X-API-Key", "change-me-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value(datasetFactory.dataset().featuredSymbol()))
                .andExpect(jsonPath("$[0].type").isNotEmpty())
                .andExpect(jsonPath("$[0].value").isNotEmpty());
    }

    @Test
    void seededQualityReportExposesIntentionalGap() throws Exception {
        DemoDatasetFactory.DemoDataset dataset = datasetFactory.dataset();

        mockMvc.perform(get("/api/v1/quality/report")
                        .header("X-API-Key", "change-me-admin")
                        .queryParam("symbol", dataset.qualitySymbol())
                        .queryParam("from", dataset.from().toString())
                        .queryParam("to", dataset.to().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value(dataset.qualitySymbol()))
                .andExpect(jsonPath("$.missingDays").value(1))
                .andExpect(jsonPath("$.missingDates[0]").value(dataset.qualityGapDate().toString()));
    }
}
