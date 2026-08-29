package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExceptionRegistryTest {

    private Path write(Path dir, String json) throws IOException {
        Path file = dir.resolve("exceptions.json");
        Files.writeString(file, json);
        return file;
    }

    private String entry(String id, String check, String reviewDate) {
        return """
                {"id":"%s","check":"%s","path":"src/main/java/X.java",
                 "why":"migration in flight","owner":"zub","reviewDate":"%s"}
                """.formatted(id, check, reviewDate);
    }

    @Test
    void reportsAnExemptionPastItsReviewDate(@TempDir Path dir) throws IOException {
        Path file = write(dir, "[" + entry("e1", "upsert-dialect", "2026-01-01") + "]");

        List<ExceptionRegistry.Entry> expired =
                ExceptionRegistry.expired(file, LocalDate.parse("2026-08-28"));

        assertEquals(1, expired.size());
        assertEquals("e1", expired.getFirst().id());
    }

    @Test
    void ignoresAnExemptionStillInDate(@TempDir Path dir) throws IOException {
        Path file = write(dir, "[" + entry("e1", "upsert-dialect", "2027-01-01") + "]");

        assertTrue(ExceptionRegistry.expired(file, LocalDate.parse("2026-08-28")).isEmpty());
    }

    @Test
    void treatsTheReviewDateItselfAsStillValid(@TempDir Path dir) throws IOException {
        Path file = write(dir, "[" + entry("e1", "upsert-dialect", "2026-08-28") + "]");

        assertTrue(ExceptionRegistry.expired(file, LocalDate.parse("2026-08-28")).isEmpty());
    }

    @Test
    void rejectsAnEntryMissingARequiredField(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                [{"id":"e1","check":"upsert-dialect","path":"X.java","why":"w","reviewDate":"2027-01-01"}]
                """);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> ExceptionRegistry.expired(file, LocalDate.parse("2026-08-28")));

        assertTrue(thrown.getMessage().contains("owner"));
    }

    @Test
    void returnsOnlyThePathsForTheNamedCheck(@TempDir Path dir) throws IOException {
        Path file = write(dir, "[" + entry("e1", "upsert-dialect", "2027-01-01") + ","
                + entry("e2", "other-check", "2027-01-01") + "]");

        assertEquals(Set.of("src/main/java/X.java"),
                ExceptionRegistry.pathsFor(file, "upsert-dialect"));
    }

    @Test
    void theRealRegistryHasNoExpiredEntries() {
        assertTrue(
                ExceptionRegistry.expired(ExceptionRegistry.DEFAULT_PATH, LocalDate.now()).isEmpty(),
                "An exemption is past its reviewDate. Fix the code and remove it, "
                        + "or extend reviewDate with a reason.");
    }
}
