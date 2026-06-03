package com.zubairmuwwakil.marketdata.demo;

import com.zubairmuwwakil.marketdata.config.ApiKeyProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("demo")
@RequestMapping("/api/v1/demo")
public class DemoConfigController {

    public record DemoConfigResponse(
            boolean demoMode,
            String defaultApiKey,
            String userApiKey,
            String adminApiKey,
            boolean providerKeyOptional,
            String featuredSymbol,
            String qualitySymbol,
            String actionSymbol,
            String defaultFrom,
            String defaultTo,
            String qualityGapDate,
            String calendarFrom,
            String calendarTo
    ) {
    }

    private final DemoDatasetFactory datasetFactory;
    private final ApiKeyProperties apiKeyProperties;

    public DemoConfigController(DemoDatasetFactory datasetFactory, ApiKeyProperties apiKeyProperties) {
        this.datasetFactory = datasetFactory;
        this.apiKeyProperties = apiKeyProperties;
    }

    @GetMapping("/config")
    public DemoConfigResponse config() {
        DemoDatasetFactory.DemoDataset dataset = datasetFactory.dataset();
        String userApiKey = keyForRole("USER");
        String adminApiKey = keyForRole("ADMIN");
        String defaultApiKey = !adminApiKey.isBlank() ? adminApiKey : userApiKey;

        return new DemoConfigResponse(
                true,
                defaultApiKey,
                userApiKey,
                adminApiKey,
                true,
                dataset.featuredSymbol(),
                dataset.qualitySymbol(),
                dataset.actionSymbol(),
                dataset.from().toString(),
                dataset.to().toString(),
                dataset.qualityGapDate().toString(),
                dataset.calendarFrom().toString(),
                dataset.calendarTo().toString()
        );
    }

    private String keyForRole(String role) {
        if (apiKeyProperties == null || apiKeyProperties.getApiKeys() == null) {
            return "";
        }
        return apiKeyProperties.getApiKeys().stream()
                .filter(entry -> entry != null && entry.role() != null && role.equalsIgnoreCase(entry.role()))
                .map(ApiKeyProperties.ApiKeyEntry::key)
                .filter(key -> key != null && !key.isBlank())
                .findFirst()
                .map(String::trim)
                .orElse("");
    }
}
