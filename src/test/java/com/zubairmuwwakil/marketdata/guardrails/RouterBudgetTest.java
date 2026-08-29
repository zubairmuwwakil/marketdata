package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The milestone exists to shrink this. A test, not a hope. */
class RouterBudgetTest {

    private static final int BUDGET_CHARS = 2400; // ~600 tokens at 4 chars/token

    private static String read(String name) {
        return SourceScanner.read(Path.of(name));
    }

    @Test
    void agentsMdStaysWithinFortyLines() {
        long lines = Arrays.stream(read("AGENTS.md").split("\n"))
                .filter(line -> !line.isBlank())
                .count();

        assertTrue(lines <= 40, "AGENTS.md is the router, not the manual. Lines: " + lines);
    }

    @Test
    void combinedAlwaysLoadedContextStaysUnderBudget() {
        int total = read("AGENTS.md").length() + read("CLAUDE.md").length();

        assertTrue(total <= BUDGET_CHARS, "Always-loaded chars: " + total + " > " + BUDGET_CHARS);
    }

    @Test
    void everyDemotedPolicyIsReachableFromTheRouter() {
        String router = read("AGENTS.md");
        for (String target : new String[] {
            "docs/policies/quote-path.md",
            "docs/policies/api-keys.md",
            "docs/policies/providers.md",
            "docs/policies/product-surface.md",
            "docs/policies/exceptions.json"
        }) {
            assertTrue(router.contains("(" + target + ")"), "Router does not link " + target);
        }
    }
}
