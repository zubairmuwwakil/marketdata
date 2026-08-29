package com.zubairmuwwakil.marketdata.guardrails;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every guardrail exemption is data with a clock on it. An agent that hits a check
 * wrong for its task adds an entry and keeps moving; the build fails once the entry
 * is past review, so an exemption cannot become permanent by accident.
 *
 * <p>Test-scope on purpose: this is guardrail machinery, not production code.
 */
public final class ExceptionRegistry {

    public static final Path DEFAULT_PATH = Path.of("docs", "policies", "exceptions.json");

    private static final List<String> REQUIRED =
            List.of("id", "check", "path", "why", "owner", "reviewDate");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExceptionRegistry() {}

    public record Entry(
            String id, String check, String path, String why, String owner, String reviewDate) {}

    public static List<Entry> load(Path registry) {
        if (!Files.exists(registry)) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(Files.readString(registry));
            if (!root.isArray()) {
                throw new IllegalStateException(registry + ": expected a JSON array");
            }
            List<Entry> entries = new ArrayList<>();
            for (JsonNode node : root) {
                validateRequiredFields(node);
                entries.add(new Entry(
                        node.get("id").asText(),
                        node.get("check").asText(),
                        node.get("path").asText(),
                        node.get("why").asText(),
                        node.get("owner").asText(),
                        node.get("reviewDate").asText()));
            }
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Entry> expired(Path registry, LocalDate today) {
        return load(registry).stream()
                .filter(entry -> LocalDate.parse(entry.reviewDate()).isBefore(today))
                .toList();
    }

    /** Paths exempted from one check, for that check's own test to skip. */
    public static Set<String> pathsFor(String checkId) {
        return pathsFor(DEFAULT_PATH, checkId);
    }

    /** Allows guardrail tests to exercise path selection without changing repository policy. */
    static Set<String> pathsFor(Path registry, String checkId) {
        return load(registry).stream()
                .filter(entry -> entry.check().equals(checkId))
                .map(Entry::path)
                .collect(Collectors.toSet());
    }

    private static void validateRequiredFields(JsonNode node) {
        for (String field : REQUIRED) {
            if (!node.hasNonNull(field) || node.get(field).asText().isEmpty()) {
                throw new IllegalStateException(
                        "exception %s: missing required field \"%s\""
                                .formatted(node.path("id").asText("(no id)"), field));
            }
        }
    }
}
