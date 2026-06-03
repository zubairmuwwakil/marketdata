package com.zubairmuwwakil.marketdata;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

@Testcontainers
class RenderDeploySmokeIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("marketdata")
            .withUsername("marketdata")
            .withPassword("marketdata");

    @Test
    @Timeout(120)
    void packagedApplicationBootsWithRenderConnectionStringAndServesHealth() throws Exception {
        Path jar = packagedJar();
        int port = freePort();
        Path output = Files.createTempFile("render-deploy-smoke", ".log");
        Process process = startApplication(jar, port, output);

        try {
            awaitHealthy(process, port, output);
            assertFlywayHistoryExists();
            assertTableExists("price_candle");
        } finally {
            stop(process);
        }
    }

    private Process startApplication(Path jar, int port, Path output) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                "java",
                "-Dserver.port=" + port,
                "-Dlogging.file.name=",
                "-jar",
                jar.toString()
        );
        builder.directory(Path.of("").toAbsolutePath().toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(output.toFile());

        builder.environment().remove("SPRING_DATASOURCE_URL");
        builder.environment().remove("SPRING_DATASOURCE_USERNAME");
        builder.environment().remove("SPRING_DATASOURCE_PASSWORD");
        builder.environment().remove("SPRING_FLYWAY_URL");
        builder.environment().remove("SPRING_FLYWAY_USER");
        builder.environment().remove("SPRING_FLYWAY_PASSWORD");
        builder.environment().put("DATABASE_URL", renderConnectionString());
        builder.environment().put("MARKETDATA_ADMIN_KEY", "smoke-admin");
        builder.environment().put("MARKETDATA_USER_KEY", "smoke-user");

        return builder.start();
    }

    private void awaitHealthy(Process process, int port, Path output) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        Throwable lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                Assertions.fail("Application exited before reporting healthy.\n" + processOutput(output));
            }

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    Assertions.assertThat(response.body()).contains("\"status\":\"UP\"");
                    return;
                }
                lastFailure = new IllegalStateException("Unexpected health status " + response.statusCode() + ": " + response.body());
            } catch (IOException e) {
                lastFailure = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }

            Thread.sleep(1_000);
        }

        Assertions.fail("Timed out waiting for health endpoint." + failureSuffix(lastFailure) + "\n" + processOutput(output));
    }

    private void assertFlywayHistoryExists() throws SQLException {
        try (Connection connection = databaseConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select count(*) from flyway_schema_history where success")) {
            Assertions.assertThat(rs.next()).isTrue();
            Assertions.assertThat(rs.getInt(1)).isGreaterThan(0);
        }
    }

    private void assertTableExists(String tableName) throws SQLException {
        String query = """
                select exists (
                    select 1
                    from information_schema.tables
                    where table_schema = 'public'
                      and table_name = '%s'
                )
                """.formatted(tableName);

        try (Connection connection = databaseConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            Assertions.assertThat(rs.next()).isTrue();
            Assertions.assertThat(rs.getBoolean(1)).isTrue();
        }
    }

    private Connection databaseConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private String renderConnectionString() {
        URI jdbcUri = URI.create(postgres.getJdbcUrl().substring("jdbc:".length()));
        StringBuilder url = new StringBuilder("postgresql://")
                .append(postgres.getUsername())
                .append(':')
                .append(postgres.getPassword())
                .append('@')
                .append(jdbcUri.getHost());
        if (jdbcUri.getPort() != -1) {
            url.append(':').append(jdbcUri.getPort());
        }
        url.append(jdbcUri.getPath());
        if (jdbcUri.getRawQuery() != null && !jdbcUri.getRawQuery().isBlank()) {
            url.append('?').append(jdbcUri.getRawQuery());
        }
        return url.toString();
    }

    private Path packagedJar() throws IOException {
        Path targetDir = Path.of("target");
        try (Stream<Path> files = Files.list(targetDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().endsWith(".jar.original"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Packaged application jar not found in " + targetDir.toAbsolutePath()));
        }
    }

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void stop(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private String processOutput(Path output) throws IOException {
        return Files.exists(output) ? Files.readString(output) : "";
    }

    private String failureSuffix(Throwable lastFailure) {
        return lastFailure == null ? "" : " Last error: " + lastFailure.getMessage();
    }
}
