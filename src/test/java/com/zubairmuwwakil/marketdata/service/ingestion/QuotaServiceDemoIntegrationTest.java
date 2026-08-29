package com.zubairmuwwakil.marketdata.service.ingestion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Ensures the quota race-safe insert also works in the standalone H2 demo profile. */
@SpringBootTest
@ActiveProfiles("demo")
class QuotaServiceDemoIntegrationTest {

    @Autowired
    private QuotaService quotaService;

    @Test
    void createsAndConsumesQuotaRowsOnH2() {
        String provider = "H2-QUOTA-" + UUID.randomUUID().toString().substring(0, 8);

        assertTrue(quotaService.tryConsumeOneCall(provider, 2));
        assertTrue(quotaService.tryConsumeOneCall(provider, 2));
        assertFalse(quotaService.tryConsumeOneCall(provider, 2));
    }
}
