package com.zubairmuwwakil.marketdata;

import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MarketdataApplicationDatasourceTest {

    @Test
    void normalizesNorthflankPostgresUri() {
        Map<String, String> env = Map.of(
                "POSTGRES_URI", "postgresql://northflank_user:secretpass@addon-host.northflank.app:5432/marketdata_db"
        );
        Map<String, String> properties = new HashMap<>();

        MarketdataApplication.normalizeDatasourceProperties(env::get, properties::put);

        Assertions.assertThat(properties.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://addon-host.northflank.app:5432/marketdata_db");
        Assertions.assertThat(properties.get("spring.datasource.username")).isEqualTo("northflank_user");
        Assertions.assertThat(properties.get("spring.datasource.password")).isEqualTo("secretpass");
        Assertions.assertThat(properties.get("spring.flyway.url"))
                .isEqualTo("jdbc:postgresql://addon-host.northflank.app:5432/marketdata_db");
        Assertions.assertThat(properties.get("spring.flyway.user")).isEqualTo("northflank_user");
        Assertions.assertThat(properties.get("spring.flyway.password")).isEqualTo("secretpass");
    }

    @Test
    void normalizesNorthflankIndividualEnvVars() {
        Map<String, String> env = Map.of(
                "POSTGRES_HOST", "postgres.northflank.internal",
                "POSTGRES_PORT", "5432",
                "POSTGRES_DATABASE", "marketdata",
                "POSTGRES_USERNAME", "admin",
                "POSTGRES_PASSWORD", "topsecret"
        );
        Map<String, String> properties = new HashMap<>();

        MarketdataApplication.normalizeDatasourceProperties(env::get, properties::put);

        Assertions.assertThat(properties.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://postgres.northflank.internal:5432/marketdata");
        Assertions.assertThat(properties.get("spring.datasource.username")).isEqualTo("admin");
        Assertions.assertThat(properties.get("spring.datasource.password")).isEqualTo("topsecret");
    }

    @Test
    void normalizesDatabaseUrl() {
        Map<String, String> env = Map.of(
                "DATABASE_URL", "postgresql://render_user:render_pass@dpg-host:5432/marketdata"
        );
        Map<String, String> properties = new HashMap<>();

        MarketdataApplication.normalizeDatasourceProperties(env::get, properties::put);

        Assertions.assertThat(properties.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dpg-host:5432/marketdata");
        Assertions.assertThat(properties.get("spring.datasource.username")).isEqualTo("render_user");
        Assertions.assertThat(properties.get("spring.datasource.password")).isEqualTo("render_pass");
    }

    @Test
    void normalizesJdbcUrlDirectly() {
        Map<String, String> env = Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5433/marketdata?sslmode=disable"
        );
        Map<String, String> properties = new HashMap<>();

        MarketdataApplication.normalizeDatasourceProperties(env::get, properties::put);

        Assertions.assertThat(properties.get("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5433/marketdata?sslmode=disable");
    }
}
