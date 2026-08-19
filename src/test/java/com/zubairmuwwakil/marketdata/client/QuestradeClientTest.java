package com.zubairmuwwakil.marketdata.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.config.ExternalApiProperties;
import com.zubairmuwwakil.marketdata.config.ProviderProperties;
import com.zubairmuwwakil.marketdata.security.ProviderCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestradeClientTest {

    private QuestradeClient client;
    private ProviderProperties providerProperties;

    @BeforeEach
    void setUp() {
        providerProperties = new ProviderProperties();
        providerProperties.getQuestrade().setAccessToken("configured-test-token");
        client = new QuestradeClient(providerProperties, new ExternalApiProperties(), new ObjectMapper());
    }

    @Test
    void throwsIllegalStateExceptionWhenNoCredentialIsAvailable() {
        providerProperties.getQuestrade().setAccessToken("");
        QuestradeClient unconfiguredClient = new QuestradeClient(providerProperties, new ExternalApiProperties(), new ObjectMapper());

        assertThatThrownBy(() -> unconfiguredClient.searchSymbol("AAPL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Questrade credential available");
    }

    @Test
    void userKeyOverrideInProviderCredentialsWinsOverAppToken() {
        ProviderCredentials.callWith(Map.of("QUESTRADE", "user-bearer-token@https://custom.api.questrade.com"), () -> {
            assertThat(ProviderCredentials.forProvider("QUESTRADE"))
                    .contains("user-bearer-token@https://custom.api.questrade.com");
            return null;
        });
    }
}
