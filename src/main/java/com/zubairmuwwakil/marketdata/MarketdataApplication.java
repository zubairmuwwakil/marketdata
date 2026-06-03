package com.zubairmuwwakil.marketdata;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@EnableScheduling
@SpringBootApplication
public class MarketdataApplication {
    public static void main(String[] args) {
        normalizeDatasourcePropertiesFromEnv();
        SpringApplication.run(MarketdataApplication.class, args);
    }

    private static void normalizeDatasourcePropertiesFromEnv() {
        normalizeConnection(
                firstNonBlank(System.getenv("SPRING_DATASOURCE_URL"), System.getenv("DATABASE_URL")),
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password"
        );
        normalizeConnection(
                firstNonBlank(System.getenv("SPRING_FLYWAY_URL"), System.getenv("SPRING_DATASOURCE_URL"), System.getenv("DATABASE_URL")),
                "spring.flyway.url",
                "spring.flyway.user",
                "spring.flyway.password"
        );
    }

    private static void normalizeConnection(String rawUrl, String urlProperty, String userProperty, String passwordProperty) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        if (rawUrl.startsWith("jdbc:")) {
            ConnectionSettings settings = parseConnectionSettings(rawUrl.substring("jdbc:".length()));
            if (settings == null) {
                setIfAbsent(urlProperty, rawUrl);
                return;
            }
            setIfAbsent(urlProperty, settings.jdbcUrl());
            setIfAbsent(userProperty, settings.username());
            setIfAbsent(passwordProperty, settings.password());
            return;
        }

        if (!rawUrl.startsWith("postgres://") && !rawUrl.startsWith("postgresql://")) {
            return;
        }

        ConnectionSettings settings = parseConnectionSettings(rawUrl);
        if (settings == null) {
            return;
        }

        setIfAbsent(urlProperty, settings.jdbcUrl());
        setIfAbsent(userProperty, settings.username());
        setIfAbsent(passwordProperty, settings.password());
    }

    private static ConnectionSettings parseConnectionSettings(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String host = uri.getHost();
            String database = uri.getPath() == null ? "" : uri.getPath();
            if (host == null || database.isBlank()) {
                return null;
            }

            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                    .append(host);
            if (uri.getPort() != -1) {
                jdbcUrl.append(':').append(uri.getPort());
            }
            jdbcUrl.append(database);
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                jdbcUrl.append('?').append(uri.getRawQuery());
            }

            String[] credentials = splitUserInfo(uri.getUserInfo());
            return new ConnectionSettings(jdbcUrl.toString(), credentials[0], credentials[1]);
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static String[] splitUserInfo(String userInfo) {
        if (userInfo == null || userInfo.isBlank()) {
            return new String[] {null, null};
        }
        String[] parts = userInfo.split(":", 2);
        return new String[] {parts[0], parts.length > 1 ? parts[1] : null};
    }

    private static void setIfAbsent(String property, String value) {
        if (value == null || value.isBlank() || System.getProperty(property) != null) {
            return;
        }
        System.setProperty(property, value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ConnectionSettings(String jdbcUrl, String username, String password) {
    }
}
