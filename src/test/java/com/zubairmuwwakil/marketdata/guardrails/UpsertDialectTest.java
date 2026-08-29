package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * H2 backs the demo profile, which is a product surface, and it has no PostgreSQL
 * {@code ON CONFLICT} support. Writers must therefore take their database choice
 * from one shared detection point.
 */
class UpsertDialectTest {

    private static final String DIALECT = "DatabaseDialect";

    @Test
    void everyClassWritingDialectSpecificSqlRoutesThroughDatabaseDialect() {
        Set<String> exempt = ExceptionRegistry.pathsFor("upsert-dialect");
        List<String> offenders = SourceScanner.mainSources().stream()
                .filter(path -> !path.getFileName().toString().equals("DatabaseDialect.java"))
                .filter(path -> !exempt.contains(SourceScanner.relative(path)))
                .filter(path -> {
                    String source = SourceScanner.read(path);
                    boolean dialectSql = source.contains("ON CONFLICT") || source.contains("MERGE INTO");
                    return dialectSql && !source.contains(DIALECT);
                })
                .map(SourceScanner::relative)
                .toList();

        assertTrue(
                offenders.isEmpty(),
                "These write dialect-specific SQL without going through DatabaseDialect, "
                        + "so demo mode (H2) and production (Postgres) can drift apart: "
                        + offenders);
    }

    @Test
    void onlyDatabaseDialectDetectsH2() {
        List<String> offenders = SourceScanner.mainSources().stream()
                .filter(path -> !path.getFileName().toString().equals("DatabaseDialect.java"))
                .filter(path -> SourceScanner.read(path)
                        .matches("(?s).*\\bprivate\\b[^;{]*\\bdetectH2\\b.*"))
                .map(SourceScanner::relative)
                .toList();

        assertTrue(
                offenders.isEmpty(),
                "A second, independent H2 detection can be fixed in one place and missed in "
                        + "the other. Call DatabaseDialect.isH2(dataSource): " + offenders);
    }
}
