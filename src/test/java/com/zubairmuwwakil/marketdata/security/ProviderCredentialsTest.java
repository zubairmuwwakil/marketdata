package com.zubairmuwwakil.marketdata.security;

import com.zubairmuwwakil.marketdata.model.KeySource;
import com.zubairmuwwakil.marketdata.service.ingestion.ProviderKeyStore;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCredentialsTest {

    @Test
    void parsesMultipleProvidersAndNormalizesTheNames() {
        Map<String, String> parsed = ProviderCredentials.parse("alphavantage=abc123, CoinGecko = def456 ");

        assertThat(parsed).containsEntry("ALPHAVANTAGE", "abc123").containsEntry("COINGECKO", "def456");
    }

    @Test
    void malformedPairsAreSkippedSoOneFumbledKeyDoesNotLoseTheOthers() {
        Map<String, String> parsed = ProviderCredentials.parse("ALPHAVANTAGE=abc,garbage,=nokey,COINGECKO=def");

        assertThat(parsed).hasSize(2).containsKeys("ALPHAVANTAGE", "COINGECKO");
    }

    @Test
    void nullAndBlankHeadersYieldNothingRatherThanThrowing() {
        assertThat(ProviderCredentials.parse(null)).isEmpty();
        assertThat(ProviderCredentials.parse("   ")).isEmpty();
    }

    @Test
    void callersOwnKeyWinsOverTheAppKey() {
        ProviderKeyStore store = new ProviderKeyStore();
        store.set("app-level-key");

        ProviderCredentials.callWith(Map.of("ALPHAVANTAGE", "callers-key"), () -> {
            ProviderKeyStore.ResolvedKey resolved = store.resolve("ALPHAVANTAGE");
            assertThat(resolved.key()).isEqualTo("callers-key");
            assertThat(resolved.source()).isEqualTo(KeySource.USER);
            return null;
        });

        // Falls back once the caller's binding is gone.
        assertThat(store.resolve("ALPHAVANTAGE").source()).isEqualTo(KeySource.APP);
    }

    @Test
    void bindingIsRestoredNotJustClearedSoPooledThreadsCannotLeakCredentials() {
        ProviderCredentials.callWith(Map.of("ALPHAVANTAGE", "outer"), () -> {
            ProviderCredentials.callWith(Map.of("ALPHAVANTAGE", "inner"), () -> {
                assertThat(ProviderCredentials.forProvider("ALPHAVANTAGE")).contains("inner");
                return null;
            });
            assertThat(ProviderCredentials.forProvider("ALPHAVANTAGE")).contains("outer");
            return null;
        });
        assertThat(ProviderCredentials.forProvider("ALPHAVANTAGE")).isEmpty();
    }

    @Test
    void workerThreadsDoNotInheritTheBindingUnlessItIsHandedToThem() throws Exception {
        ProviderKeyStore store = new ProviderKeyStore();
        store.set("app-level-key");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ProviderCredentials.callWith(Map.of("ALPHAVANTAGE", "callers-key"), () -> {
                Map<String, String> snapshot = ProviderCredentials.snapshot();
                try {
                    // Without the snapshot the worker silently degrades to the app key,
                    // which is the bug this plumbing exists to prevent.
                    KeySource naive = executor.submit(() -> store.resolve("ALPHAVANTAGE").source()).get();
                    KeySource handed = executor.submit(() ->
                            ProviderCredentials.callWith(snapshot, () -> store.resolve("ALPHAVANTAGE").source())).get();

                    assertThat(naive).isEqualTo(KeySource.APP);
                    assertThat(handed).isEqualTo(KeySource.USER);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        }
    }

    @Test
    void anAbsentAppKeyIsReportedAsAbsentRatherThanAsAnEmptyString() {
        ProviderKeyStore store = new ProviderKeyStore();

        assertThat(store.isConfigured()).isFalse();
        assertThat(store.resolve("ALPHAVANTAGE").isPresent()).isFalse();
    }
}
