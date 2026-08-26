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

    static void normalizeDatasourcePropertiesFromEnv() {
        normalizeDatasourceProperties(System::getenv, MarketdataApplication::setIfAbsent);
    }

    static void normalizeDatasourceProperties(
            java.util.function.Function<String, String> envLookup,
            java.util.function.BiConsumer<String, String> propertySetter
    ) {
        String rawUrl = firstNonBlank(
                envLookup.apply("SPRING_DATASOURCE_URL"),
                envLookup.apply("DATABASE_URL"),
                envLookup.apply("POSTGRES_URI"),
                envLookup.apply("POSTGRESQL_URI"),
                envLookup.apply("NF_POSTGRES_DB_URI"),
                buildFromIndividualEnvVars(envLookup)
        );

        normalizeConnection(
                rawUrl,
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                propertySetter
        );
        normalizeConnection(
                firstNonBlank(envLookup.apply("SPRING_FLYWAY_URL"), rawUrl),
                "spring.flyway.url",
                "spring.flyway.user",
                "spring.flyway.password",
                propertySetter
        );
    }

    private static String buildFromIndividualEnvVars(java.util.function.Function<String, String> envLookup) {
        String host = envLookup.apply("POSTGRES_HOST");
        if (host == null || host.isBlank()) {
            return null;
        }
        String port = firstNonBlank(envLookup.apply("POSTGRES_PORT"), "5432");
        String db = firstNonBlank(envLookup.apply("POSTGRES_DATABASE"), envLookup.apply("POSTGRES_DB"), "marketdata");
        String user = firstNonBlank(envLookup.apply("POSTGRES_USERNAME"), envLookup.apply("POSTGRES_USER"), "");
        String pass = firstNonBlank(envLookup.apply("POSTGRES_PASSWORD"), "");

        if (!user.isBlank() && !pass.isBlank()) {
            return "postgresql://" + user + ":" + pass + "@" + host + ":" + port + "/" + db;
        } else if (!user.isBlank()) {
            return "postgresql://" + user + "@" + host + ":" + port + "/" + db;
        }
        return "postgresql://" + host + ":" + port + "/" + db;
    }

    private static void normalizeConnection(
            String rawUrl,
            String urlProperty,
            String userProperty,
            String passwordProperty,
            java.util.function.BiConsumer<String, String> propertySetter
    ) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        if (rawUrl.startsWith("jdbc:")) {
            ConnectionSettings settings = parseConnectionSettings(rawUrl.substring("jdbc:".length()));
            if (settings == null) {
                propertySetter.accept(urlProperty, rawUrl);
                return;
            }
            propertySetter.accept(urlProperty, settings.jdbcUrl());
            propertySetter.accept(userProperty, settings.username());
            propertySetter.accept(passwordProperty, settings.password());
            return;
        }

        if (!rawUrl.startsWith("postgres://") && !rawUrl.startsWith("postgresql://")) {
            return;
        }

        ConnectionSettings settings = parseConnectionSettings(rawUrl);
        if (settings == null) {
            return;
        }

        propertySetter.accept(urlProperty, settings.jdbcUrl());
        propertySetter.accept(userProperty, settings.username());
        propertySetter.accept(passwordProperty, settings.password());
    }

    private static final java.util.regex.Pattern DB_URI_PATTERN = java.util.regex.Pattern.compile(
            "^(?:jdbc:)?postgres(?:ql)?://(?:([^:@/]+)(?::([^@/]*))?@)?([^:/]+)(?::(\\d+))?(?:/([^?]+))?(?:\\?(.*))?$"
    );

    private static ConnectionSettings parseConnectionSettings(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }

        var matcher = DB_URI_PATTERN.matcher(rawUrl.trim());
        if (!matcher.matches()) {
            return null;
        }

        String rawUser = matcher.group(1);
        String rawPass = matcher.group(2);
        String host = matcher.group(3);
        String port = matcher.group(4);
        String db = matcher.group(5);
        String query = matcher.group(6);

        if (host == null || host.isBlank()) {
            return null;
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(host);
        if (port != null && !port.isBlank()) {
            jdbcUrl.append(':').append(port);
        }
        if (db != null && !db.isBlank()) {
            jdbcUrl.append('/').append(db);
        }
        if (query != null && !query.isBlank()) {
            jdbcUrl.append('?').append(query);
        }

        String username = rawUser != null ? java.net.URLDecoder.decode(rawUser, java.nio.charset.StandardCharsets.UTF_8) : null;
        String password = rawPass != null ? java.net.URLDecoder.decode(rawPass, java.nio.charset.StandardCharsets.UTF_8) : null;

        return new ConnectionSettings(jdbcUrl.toString(), username, password);
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
