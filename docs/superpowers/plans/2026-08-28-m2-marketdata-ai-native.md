# M2 — MarketLens AI-Native Scaffolding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cut marketdata's always-loaded agent context from ~2,385 tokens to ≤600 by compiling seven invariants into tests that run in the existing surefire pass, demoting the rest to linked policy files, and gating the result behind required status checks.

**Architecture:** `AGENTS.md` becomes the canonical router; `CLAUDE.md` shrinks to a pointer. Checks are **plain JUnit 5 tests under `guardrails/`**, not a new library — see the ArchUnit decision below. They read source files from `src/main/java`, so they run in `./mvnw verify` with no new CI plumbing and no new dependency.

**Tech Stack:** Java 21, Spring Boot 4.0.1, Maven, JUnit 5, Testcontainers, H2 (demo profile), GitHub Actions.

**Spec:** `../MoneyTalks/docs/superpowers/specs/2026-08-28-ai-native-repos-design.md`

## Decision: no ArchUnit — this supersedes the spec's §3

The spec says marketdata "gets ArchUnit tests inside the existing surefire run." That was written before the invariants were read against the code, and it is wrong.

ArchUnit reasons about **bytecode** — class dependencies, layering, injection targets. Of marketdata's eight compilable invariants, exactly one is that shape (no direct `MarketDataProvider` injection). The rest are **source text**: SQL literals, user-facing copy, endpoint signatures. ArchUnit is the wrong instrument, and adding a dependency to serve one rule in eight is worse than the prose it replaces.

Plain JUnit 5 tests that read `src/main/java` are the native idiom here, cost nothing, and run where every other test already runs. **Update the spec's §3 when this milestone lands.**

## Global Constraints

- **P1 Compile to delete.** Every check must retire specific always-loaded prose. Name what you deleted in the commit message.
- **P2 Compile or demote.** Nothing is deleted; what cannot become a check moves to `docs/policies/`, reached by one router line.
- **P3 One owner.** Market data is MarketLens'. Personal finance is In Unity's. Restate, never revise.
- **P4 No check ships without a trigger.** **M1 got this wrong** — its plan established the CI trigger eleven tasks late and execution had to rewrite the plan first. Task 1 here establishes the trigger before any check exists.
- **P5 Always-load the trigger, demote the procedure.**
- **Ratified and immovable:** E3, E4, A5, A6, ADR 0003.
- **Demo mode is a product surface, not a test fixture.** `./mvnw -Pdemo spring-boot:run` must need no PostgreSQL and no provider key. Any change that regresses this regresses the standalone story.
- **Router budget:** `AGENTS.md` ≤ 40 lines, combined always-loaded ≤ 2,400 characters. Asserted in Task 9.
- **Commit style:** Conventional Commits. **Never** add `Co-Authored-By` trailers.

## Preconditions

This repo is checked out on branch `northflank` with 13 uncommitted files (the quote-cause work). `main` is the trunk as of 2026-08-28. **Before starting:** confirm with the owner whether M2 lands on `main` or on `northflank`, and do not switch branches with that work in the tree. Every `git` step below assumes the answer is `main` and the tree is clean.

---

### Task 1: The one command, and the CI trigger for everything after it

Establishes the trigger **first**. Every later task appends a test to a suite CI already runs, so P4 is satisfied by construction rather than by remembering.

**Files:**
- Modify: `.github/workflows/ci.yml`
- Create: `docs/policies/exceptions.json`

**Interfaces:**
- Produces: `./mvnw verify` as the single verification entry point, invoked by CI. Consumed by every later task.

- [ ] **Step 1: Read the current CI workflow**

Run: `cat .github/workflows/ci.yml`

Note the exact job name and current build command. The `branches: [ "main" ]` trigger was corrected on 2026-08-28 when `render_2` was renamed; do not reintroduce the old name.

- [ ] **Step 2: Make CI run the full verify lifecycle**

In `.github/workflows/ci.yml`, ensure the build step is:

```yaml
      - name: Verify
        run: ./mvnw --batch-mode verify
```

`verify` runs the whole surefire suite, which is where every guardrail test in Tasks 3–7 will live. If the workflow currently runs `test` or a narrower goal, change it now — this is the trigger.

- [ ] **Step 3: Create the empty exception registry**

Create `docs/policies/exceptions.json`:

```json
[]
```

It guards nothing yet. That is deliberate: the trigger exists before the thing it guards, which is the ordering M1 got backwards.

- [ ] **Step 4: Verify locally**

Run: `./mvnw --batch-mode verify`
Expected: BUILD SUCCESS, 16 test classes green.

- [ ] **Step 5: Commit and confirm CI is green before continuing**

```bash
git add .github/workflows/ci.yml docs/policies/exceptions.json
git commit -m "ci: run the full verify lifecycle, so later guardrails have a trigger

Every check added in this milestone is a JUnit test in the existing surefire
pass. Pointing CI at verify before writing any of them means each one arrives
already triggered, rather than shipping into a void the way three exemptions did
in pickleball-session-manager."
git push
gh run list --limit 1
```

Do not start Task 2 until that run is green.

---

### Task 2: The exception registry and its expiry test

**Files:**
- Create: `src/test/java/com/zubairmuwwakil/marketdata/guardrails/ExceptionRegistry.java`
- Create: `src/test/java/com/zubairmuwwakil/marketdata/guardrails/ExceptionRegistryTest.java`

**Interfaces:**
- Produces: `ExceptionRegistry.load(Path)` → `List<Entry>`; `Entry` is a record with `id`, `check`, `path`, `why`, `owner`, `reviewDate` (all `String`).
- Produces: `ExceptionRegistry.expired(Path, LocalDate)` → `List<Entry>`.
- Produces: `ExceptionRegistry.pathsFor(String checkId)` → `Set<String>`, used by Tasks 3–6 so a check honours its exemptions.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/zubairmuwwakil/marketdata/guardrails/ExceptionRegistryTest.java`:

```java
package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
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
        assertEquals("e1", expired.get(0).id());
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
        assertEquals(1, ExceptionRegistry.load(file).stream()
                .filter(e -> e.check().equals("upsert-dialect")).count());
    }

    @Test
    void theRealRegistryHasNoExpiredEntries() {
        assertTrue(
                ExceptionRegistry.expired(ExceptionRegistry.DEFAULT_PATH, LocalDate.now()).isEmpty(),
                "An exemption is past its reviewDate. Fix the code and remove it, "
                        + "or extend reviewDate with a reason.");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw --batch-mode test -Dtest=ExceptionRegistryTest`
Expected: compilation failure — `ExceptionRegistry` does not exist.

- [ ] **Step 3: Write the registry**

Create `src/test/java/com/zubairmuwwakil/marketdata/guardrails/ExceptionRegistry.java`:

```java
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
                for (String field : REQUIRED) {
                    if (!node.hasNonNull(field) || node.get(field).asText().isEmpty()) {
                        throw new IllegalStateException(
                                "exception %s: missing required field \"%s\""
                                        .formatted(node.path("id").asText("(no id)"), field));
                    }
                }
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
                .filter(e -> LocalDate.parse(e.reviewDate()).isBefore(today))
                .toList();
    }

    /** Paths exempted from one check, for that check's own test to skip. */
    public static Set<String> pathsFor(String checkId) {
        return load(DEFAULT_PATH).stream()
                .filter(e -> e.check().equals(checkId))
                .map(Entry::path)
                .collect(Collectors.toSet());
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw --batch-mode test -Dtest=ExceptionRegistryTest`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/zubairmuwwakil/marketdata/guardrails/
git commit -m "test(guardrails): exemptions become data with an expiry

Entries carry what, why, owner and reviewDate, and the build fails once one is
past review. It runs in the existing surefire pass, so it is triggered from the
moment it exists — unlike the equivalent in pickleball-session-manager, which was
correct and unwatched for five weeks while three entries rotted."
```

---

### Task 3: Compile "every upsert routes through DatabaseDialect"

**There is a live near-miss here.** `PriceCandleUpsertRepository` carries both `MERGE INTO` and `ON CONFLICT` and implements its **own private `detectH2(DataSource)`**, duplicating `DatabaseDialect.isH2(DataSource)`. It is correct today and structurally fragile: two independent dialect detections, one of which can be fixed while the other is missed. That is exactly how `QuotaService` came to throw `BadSqlGrammarException` on every quota consumption in the demo profile.

**Files:**
- Create: `src/test/java/com/zubairmuwwakil/marketdata/guardrails/SourceScanner.java`
- Create: `src/test/java/com/zubairmuwwakil/marketdata/guardrails/UpsertDialectTest.java`
- Modify: `src/main/java/com/zubairmuwwakil/marketdata/repository/PriceCandleUpsertRepository.java`

**Interfaces:**
- Produces: `SourceScanner.mainSources()` → `List<Path>` of every `.java` under `src/main/java`.
- Produces: `SourceScanner.read(Path)` → `String`, and `SourceScanner.relative(Path)` → repo-relative path string.
- Consumes: `ExceptionRegistry.pathsFor("upsert-dialect")` from Task 2.

- [ ] **Step 1: Write the shared scanner**

Create `src/test/java/com/zubairmuwwakil/marketdata/guardrails/SourceScanner.java`:

```java
package com.zubairmuwwakil.marketdata.guardrails;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Guardrails here read source text, not bytecode. The invariants worth enforcing in
 * this repo are about SQL literals, user-facing copy and endpoint shapes — none of
 * which survive compilation in a form worth asserting against. That is why this
 * milestone uses plain JUnit rather than ArchUnit.
 */
public final class SourceScanner {

    public static final Path MAIN = Path.of("src", "main", "java");

    private SourceScanner() {}

    public static List<Path> mainSources() {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            return paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String relative(Path path) {
        return path.toString().replace('\\', '/');
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/zubairmuwwakil/marketdata/guardrails/UpsertDialectTest.java`:

```java
package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * H2 backs the demo profile, which is a product surface, and it has no ON CONFLICT.
 * A writer that hardcodes one dialect works in exactly one environment. QuotaService
 * missed this and threw BadSqlGrammarException on every quota consumption in demo —
 * swallowed by the caller, so demo quotes silently resolved nothing.
 */
class UpsertDialectTest {

    private static final String DIALECT = "DatabaseDialect";

    @Test
    void everyClassWritingDialectSpecificSqlRoutesThroughDatabaseDialect() {
        Set<String> exempt = ExceptionRegistry.pathsFor("upsert-dialect");
        List<String> offenders = SourceScanner.mainSources().stream()
                .filter(p -> !p.getFileName().toString().equals("DatabaseDialect.java"))
                .filter(p -> !exempt.contains(SourceScanner.relative(p)))
                .filter(p -> {
                    String src = SourceScanner.read(p);
                    boolean dialectSql = src.contains("ON CONFLICT") || src.contains("MERGE INTO");
                    return dialectSql && !src.contains(DIALECT);
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
                .filter(p -> !p.getFileName().toString().equals("DatabaseDialect.java"))
                .filter(p -> SourceScanner.read(p).matches("(?s).*\\bprivate\\b[^;{]*\\bdetectH2\\b.*"))
                .map(SourceScanner::relative)
                .toList();

        assertTrue(
                offenders.isEmpty(),
                "A second, independent H2 detection can be fixed in one place and missed in "
                        + "the other. Call DatabaseDialect.isH2(dataSource): " + offenders);
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./mvnw --batch-mode test -Dtest=UpsertDialectTest`
Expected: **both tests FAIL**, naming `src/main/java/com/zubairmuwwakil/marketdata/repository/PriceCandleUpsertRepository.java`. This is the live near-miss — the check earns its place before you fix anything.

- [ ] **Step 4: Fix the production code**

In `PriceCandleUpsertRepository`, delete the private `detectH2(DataSource)` method and route through the existing helper:

```java
import com.zubairmuwwakil.marketdata.repository.DatabaseDialect;

// in the constructor, replacing `this.h2Database = detectH2(jdbcTemplate.getDataSource());`
this.h2Database = DatabaseDialect.isH2(jdbcTemplate.getDataSource());
```

Read the file first — keep its existing field name and constructor shape. Do not change which SQL runs; both branches are already correct. This removes the *duplicate detection*, not the dialect split.

- [ ] **Step 5: Run to verify both pass**

Run: `./mvnw --batch-mode test -Dtest=UpsertDialectTest`
Expected: 2 tests pass.

- [ ] **Step 6: Verify nothing else broke, in both profiles**

Run: `./mvnw --batch-mode verify`
Run: `./mvnw --batch-mode -Pdemo test`
Expected: both BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/com/zubairmuwwakil/marketdata/guardrails/ \
        src/main/java/com/zubairmuwwakil/marketdata/repository/PriceCandleUpsertRepository.java
git commit -m "test(guardrails): every dialect-specific writer goes through DatabaseDialect

The check found a live near-miss on its first run: PriceCandleUpsertRepository
carried its own private detectH2, a second independent dialect detection beside
DatabaseDialect.isH2. Correct today, and exactly the shape that let QuotaService
throw BadSqlGrammarException on every demo-profile quota consumption. Removed the
duplicate; the SQL split itself is unchanged."
```

---

### Task 4: Compile "providers resolve through the registry, never by direct injection"

**Files:**
- Create: `src/test/java/com/zubairmuwwakil/marketdata/guardrails/ProviderResolutionTest.java`

**Interfaces:**
- Consumes: `SourceScanner` (Task 3), `ExceptionRegistry.pathsFor` (Task 2).

- [ ] **Step 1: Read how the registry is used today**

Run: `grep -rn "MarketDataProviderRegistry" src/main --include='*.java'`
Run: `sed -n '1,40p' src/main/java/com/zubairmuwwakil/marketdata/service/ingestion/MarketDataProviderRegistry.java`

Confirm the resolution signature (it resolves by `(AssetClass, capability)`). The test must not forbid the registry from holding providers itself, nor a class from *implementing* the interface — only from taking one as a collaborator.

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/zubairmuwwakil/marketdata/guardrails/ProviderResolutionTest.java`:

```java
package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Provider routing is by purpose, not one global default: curated watchlist goes to
 * Alpha Vantage (sanctioned, 25/day), dynamic per-user symbols to Yahoo (E4,
 * unsanctioned, has headroom). Both are daily closes — the split is sanction and
 * quota, never latency. Injecting a MarketDataProvider directly picks one at wiring
 * time and silently defeats that.
 */
class ProviderResolutionTest {

    /** A constructor parameter or field typed MarketDataProvider — i.e. taking one as a collaborator. */
    private static final Pattern INJECTED =
            Pattern.compile("(?<![A-Za-z])MarketDataProvider\\s+[a-z][A-Za-z0-9]*\\s*[,;)]");

    @Test
    void noClassInjectsAMarketDataProviderDirectly() {
        Set<String> exempt = ExceptionRegistry.pathsFor("provider-resolution");
        List<String> offenders = SourceScanner.mainSources().stream()
                .filter(p -> !exempt.contains(SourceScanner.relative(p)))
                .filter(p -> {
                    String src = SourceScanner.read(p);
                    // Implementing the interface is how a provider is written; the registry
                    // is where they legitimately collect.
                    if (src.contains("implements MarketDataProvider")
                            || src.contains("class MarketDataProviderRegistry")) {
                        return false;
                    }
                    return INJECTED.matcher(src).find();
                })
                .map(SourceScanner::relative)
                .toList();

        assertTrue(
                offenders.isEmpty(),
                "Resolve providers through MarketDataProviderRegistry by (AssetClass, "
                        + "capability). Injecting one directly picks a provider at wiring "
                        + "time and defeats routing-by-purpose: " + offenders);
    }
}
```

- [ ] **Step 3: Run it**

Run: `./mvnw --batch-mode test -Dtest=ProviderResolutionTest`

Expected: PASS — `grep` on 2026-08-28 found only `DemoMarketDataProvider`, which *implements* the interface and is excluded. **If it fails**, read each offender: either it is a genuine violation (route it through the registry) or the regex is over-broad (narrow it). Do not add a registry exemption to make a green test.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/zubairmuwwakil/marketdata/guardrails/ProviderResolutionTest.java
git commit -m "test(guardrails): providers resolve through the registry, not by injection

Routing is by purpose — sanctioned Alpha Vantage for the curated watchlist,
Yahoo for dynamic per-user symbols. Injecting a MarketDataProvider directly picks
one at wiring time and silently defeats that split."
```

---

### Task 5: Compile "no consumer-shaped endpoints" (E3/A5)

MarketLens stands alone as its own product. A portfolio-valuation endpoint — quantities in, a total out — would make this service own the complete financial picture and weld it to one caller's data model. Symbols in, prices-with-currency-and-staleness out.

**Files:**
- Create: `src/test/java/com/zubairmuwwakil/marketdata/guardrails/ServiceBoundaryTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/zubairmuwwakil/marketdata/guardrails/ServiceBoundaryTest.java`:

```java
package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * E3/A5: MarketLens answers "what is this security worth and how has it behaved".
 * Purchases, cards, budgets and the complete financial picture belong to In Unity.
 * The specific line: no endpoint takes quantities and returns a total.
 */
class ServiceBoundaryTest {

    /** Consumer-shaped inputs: a holding size, not a symbol. */
    private static final Pattern QUANTITY_INPUT = Pattern.compile(
            "(?i)\\b(quantity|quantities|shares|units|holdings?|positions?|costBasis|bookValue)\\b");

    private static boolean isController(Path p) {
        return p.getFileName().toString().endsWith("Controller.java");
    }

    @Test
    void noControllerAcceptsHoldingQuantities() {
        Set<String> exempt = ExceptionRegistry.pathsFor("service-boundary");
        List<String> offenders = SourceScanner.mainSources().stream()
                .filter(ServiceBoundaryTest::isController)
                .filter(p -> !exempt.contains(SourceScanner.relative(p)))
                .filter(p -> QUANTITY_INPUT.matcher(SourceScanner.read(p)).find())
                .map(SourceScanner::relative)
                .toList();

        assertTrue(
                offenders.isEmpty(),
                "An endpoint taking quantities makes this service own the complete "
                        + "financial picture and welds it to one caller's data model. "
                        + "Symbols in, prices-with-currency-and-staleness out: " + offenders);
    }

    @Test
    void noPortfolioOrValuationRouteExists() {
        List<String> offenders = SourceScanner.mainSources().stream()
                .filter(ServiceBoundaryTest::isController)
                .filter(p -> SourceScanner.read(p)
                        .matches("(?is).*@(Get|Post)Mapping\\([^)]*(portfolio|net-?worth|valuation).*"))
                .map(SourceScanner::relative)
                .toList();

        assertTrue(offenders.isEmpty(), "Portfolio valuation is the hub's, not MarketLens': " + offenders);
    }
}
```

- [ ] **Step 2: Run it and triage honestly**

Run: `./mvnw --batch-mode test -Dtest=ServiceBoundaryTest`

There are 18 controllers. If either test fails, read the hit before reacting: a `WatchlistController` mentioning "positions" in a comment is a false positive worth narrowing the regex for; an endpoint genuinely accepting quantities is an E3 violation and the endpoint is wrong, not the test.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/zubairmuwwakil/marketdata/guardrails/ServiceBoundaryTest.java
git commit -m "test(guardrails): no consumer-shaped endpoint may appear here

MarketLens stands alone as its own product; In Unity is its first consumer, not
its purpose. Quantities in and a total out would weld this service to one
caller's data model."
```

---

### Task 6: Compile the honesty invariant (A6)

**Files:**
- Create: `src/test/java/com/zubairmuwwakil/marketdata/guardrails/PriceHonestyTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/zubairmuwwakil/marketdata/guardrails/PriceHonestyTest.java`:

```java
package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A6, the honesty invariant. This service serves daily closes. Saying "real-time"
 * anywhere a user can read it — API copy, the dashboard, OpenAPI descriptions — is
 * a claim the infrastructure does not support.
 */
class PriceHonestyTest {

    private static final Pattern CLAIM = Pattern.compile("(?i)real[\\s-]?time|live price|live quote");
    private static final Pattern DENIAL =
            Pattern.compile("(?i)\\b(not|never|no|rather than|instead of)\\b[^.]{0,24}real[\\s-]?time");

    private static List<Path> userFacingSources() {
        List<Path> paths = new ArrayList<>(SourceScanner.mainSources());
        Path resources = Path.of("src", "main", "resources");
        if (Files.exists(resources)) {
            try (Stream<Path> walk = Files.walk(resources)) {
                walk.filter(p -> {
                            String n = p.toString();
                            return n.endsWith(".html") || n.endsWith(".yml") || n.endsWith(".yaml");
                        })
                        .forEach(paths::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return paths;
    }

    @Test
    void nothingUserFacingClaimsRealTimePricing() {
        List<String> offenders = new ArrayList<>();
        for (Path path : userFacingSources()) {
            String[] lines = SourceScanner.read(path).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String bare = line.strip();
                // Comments are exempt: writing down WHY we don't claim it is the behaviour we want.
                if (bare.startsWith("//") || bare.startsWith("*") || bare.startsWith("/*")
                        || bare.startsWith("#") || bare.startsWith("<!--")) {
                    continue;
                }
                if (CLAIM.matcher(line).find() && !DENIAL.matcher(line).find()) {
                    offenders.add(SourceScanner.relative(path) + ":" + (i + 1));
                }
            }
        }

        assertTrue(
                offenders.isEmpty(),
                "This service serves daily closes. Say \"daily close\" or \"latest close\": "
                        + offenders);
    }
}
```

- [ ] **Step 2: Run and fix any copy it finds**

Run: `./mvnw --batch-mode test -Dtest=PriceHonestyTest`

Every hit is either honest copy to rewrite ("latest close") or a false positive to narrow. **Never add a registry exemption for copy** — an honest phrasing always exists.

- [ ] **Step 3: Commit**

```bash
git add -A src/test/java/com/zubairmuwwakil/marketdata/guardrails/PriceHonestyTest.java src/main/resources
git commit -m "test(guardrails): nothing user-facing may claim real-time pricing (A6)

Scans Java, the static dashboards and the OpenAPI copy. Comments are exempt —
recording why we don't claim it is the behaviour we want."
```

---

### Task 7: Compile "demo mode is a product surface"

`./mvnw -Pdemo spring-boot:run` must need no PostgreSQL and no provider key. Nothing currently proves that in CI, and a capability that only works against a real database regresses the standalone story silently.

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Add the demo job**

Append to `.github/workflows/ci.yml`:

```yaml
  demo-profile:
    name: demo profile (no Postgres, no provider key)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      # Deliberately no services: block and no secrets. Demo mode is a product
      # surface, not a test fixture — if it needs a database or a key to pass, the
      # standalone story has regressed and this job is the only thing that notices.
      - name: Tests under the demo profile
        run: ./mvnw --batch-mode -Pdemo test

      - name: The app boots on H2 with no provider key
        run: |
          ./mvnw --batch-mode -Pdemo spring-boot:run > /tmp/demo.log 2>&1 &
          for i in $(seq 1 60); do
            if curl -fsS http://localhost:8080/actuator/health > /dev/null 2>&1; then
              echo "demo profile is up"; exit 0
            fi
            sleep 2
          done
          echo "::error::Demo profile did not become healthy in 120s"
          tail -60 /tmp/demo.log
          exit 1
```

- [ ] **Step 2: Confirm the health path and port**

Run: `grep -rnE 'server.port|management.endpoints|actuator' src/main/resources/application*.yml`

If the demo profile uses a different port or exposes health elsewhere, correct the curl above. The job is worthless if it polls the wrong URL and times out for the wrong reason.

- [ ] **Step 3: Verify locally first**

Run: `./mvnw --batch-mode -Pdemo test`
Then, in one shell: `./mvnw -Pdemo spring-boot:run`, and in another: `curl -fsS http://localhost:8080/actuator/health`
Expected: `{"status":"UP"}` with no PostgreSQL running and `ALPHAVANTAGE_API_KEY` unset.

- [ ] **Step 4: Commit and watch the run**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: prove the demo profile still stands alone

Demo mode is a product surface, so 'needs no PostgreSQL and no provider key' has
to be asserted rather than remembered. The job takes no services and no secrets
on purpose: if it needs either to pass, the standalone story has regressed."
git push
gh run list --limit 1
```

---

### Task 8: Demote the uncompilable invariants

P2: nothing is deleted. The incident histories in `CLAUDE.md` are the most valuable prose in this repo — they stop loading on every turn and start loading when someone is in that code.

**Files:**
- Create: `docs/policies/quote-path.md`
- Create: `docs/policies/api-keys.md`
- Create: `docs/policies/providers.md`

- [ ] **Step 1: Extract the quote path**

Create `docs/policies/quote-path.md`:

```markdown
# The quote path (ADR 0003, E4)

**Read when:** touching `QuoteService`, the sweep, provider fan-out, or staleness.
**Asserted by:** `ProviderPropertiesTest`, `QuoteServiceTest`, `QuoteRefreshJobTest`.

`GET /api/v1/quotes?symbols=` serves an arbitrary symbol set: cache-first from
`price_candle`, staleness measured against the exchange calendar, bounded on-demand
fan-out, and **fail closed** — `UNAVAILABLE` with a null price, never a fabricated
one. Demand-registered symbols live in `tracked_symbol` (symbols only, never consumer
identity or quantities), separate from the curated `watchlist_symbol`.

## Five rules, each bought with an incident

1. **Every non-FRESH quote carries a `CAUSE_*` reason.** When fan-out resolves
   nothing, `buildQuote` falls back to the cached candle — and a cache hit served
   after a timeout is byte-identical to one served because the market genuinely has
   no newer close. That ambiguity hid a nightly failure at the consumer for weeks
   (`MoneyTalks/docs/decisions/LOG.md` 2026-08-27). Keep the vocabulary in sync with
   the consumer.
2. **Fan-out deadlines are sized for a cold start (45s), not interactive latency.** A
   *measured* 4-symbol fan-out took 7.74s against a hardcoded 8s deadline, so on a
   cold container it never finished. A background sweep that gives up early returns
   worse data, not faster data. `ProviderPropertiesTest` fails if anyone lowers it.
3. **`@Scheduled` is not a scheduler on a host that spins down.** A sleeping container
   fires no timers; the 22:30 UTC warm sweep had never once run in production.
   `POST /api/v1/admin/quote-sweep` is the load-bearing trigger, the timer a fallback.
4. **The sweep force-refreshes.** The cache-miss test is
   `cachedTradeDate.isBefore(expectedSession)`, so a cache holding a *wrong* candle
   for the *right* date cannot repair itself. A sweep that consults the cache is a
   no-op exactly when it matters.
5. **An in-progress session is not a close.** Yahoo's daily series includes the
   session still trading; accepting it stores an intraday price as the day's close
   *and* freezes it, since a candle dated today is never re-fetched today. Candles
   after `expectedSession` are discarded — a no-op for crypto, whose expected session
   is the current UTC day.
```

- [ ] **Step 2: Extract the key vocabulary**

Create `docs/policies/api-keys.md`:

```markdown
# Two different things are called "API key"

**Read when:** touching auth, `ApiKeyRegistry`, `ProviderKeyStore`, or BYOK.

- **Consumer keys** (`X-API-Key`, `ApiKeyRegistry`) answer *"may you call MarketLens"*.
- **Provider keys** (`X-Provider-Key: PROVIDER=key`, `ProviderKeyStore`) answer
  *"whose upstream credential do we spend"*.

Do not conflate them.

## BYOK

`X-Provider-Key` is used for one request, never persisted, never logged. **Do not add
credential storage here.** MarketLens has no encryption at rest and must not become a
credential holder — the consumer stores keys encrypted (`MoneyTalks`
`src/lib/security/providerKeys.ts`).

`keySource` on every quote reports whether the caller's key (`USER`), the app key
(`APP`), or a keyless source (`NONE`) served it. The `keys.html` provider key is an
in-memory **session override** that does not survive a restart; `ALPHAVANTAGE_API_KEY`
is the durable path.
```

- [ ] **Step 3: Extract provider routing and the currency rule**

Create `docs/policies/providers.md`:

```markdown
# Providers, routing, and currency

**Read when:** adding or changing a market-data provider, or touching `price_candle`.
**Asserted by:** `ProviderResolutionTest`, `MarketDataProviderRegistryTest`.

## Routing is by purpose, not one global default

- Curated watchlist → **Alpha Vantage** (sanctioned, 25/day).
- Dynamic per-user symbols → **Yahoo** (E4, unsanctioned, has headroom).

**Both are daily closes.** The split is sanction and quota, never latency. Resolve
through `MarketDataProviderRegistry` by `(AssetClass, capability)`; never inject a
`MarketDataProvider` bean directly.

## Prices carry a currency or they carry nothing

`price_candle.currency` is nullable and null means *"the provider did not report one"*
— never *"assume USD"*. Consumers must refuse to sum figures whose currency they
cannot prove.

## Crypto

Ratified as MarketLens' on 2026-08-18, not yet built here. Planned: Binance public
data for history (`data.binance.vision`, no key, published for bulk download — a
better risk posture than Yahoo; note it quotes **USDT**, not USD), CoinGecko for spot.
The hub's CoinGecko path is on loan until ported.

Questrade was considered and rejected for pricing: per-user OAuth with a rotating
refresh token, requires a brokerage account, and its real value is holdings sync,
which is the hub's domain.
```

- [ ] **Step 4: Confirm nothing was dropped**

Read the current `CLAUDE.md` beside these three files. Every bullet must land in a
test (Tasks 3–7), one of these files, or the router's identity lines (Task 9).

- [ ] **Step 5: Commit**

```bash
git add docs/policies/
git commit -m "docs(policies): demote the uncompilable invariants to linked files

The quote-path incidents are the most valuable prose here and were costing every
task, including tasks that never touch a quote. They now load when someone is
actually in that code."
```

---

### Task 9: The router — `AGENTS.md` canonical

marketdata has **no `AGENTS.md` at all**, so Codex, Gemini and Copilot currently see none of these boundaries.

**Files:**
- Create: `AGENTS.md`
- Modify: `CLAUDE.md` (shrink to a pointer)
- Create: `src/test/java/com/zubairmuwwakil/marketdata/guardrails/RouterBudgetTest.java`

- [ ] **Step 1: Write the failing budget test**

Create `src/test/java/com/zubairmuwwakil/marketdata/guardrails/RouterBudgetTest.java`:

```java
package com.zubairmuwwakil.marketdata.guardrails;

import static org.junit.jupiter.api.Assertions.*;

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
                .filter(l -> !l.isBlank())
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
            "docs/policies/exceptions.json"
        }) {
            assertTrue(router.contains("(" + target + ")"), "Router does not link " + target);
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw --batch-mode test -Dtest=RouterBudgetTest`
Expected: FAIL — `AGENTS.md` does not exist.

- [ ] **Step 3: Write the router**

Create `AGENTS.md`:

```markdown
# MarketLens — agent router

The market-data service of a four-product ecosystem, and **its own product**. In
Unity is its first consumer, not its purpose. This repo answers "what is this
security worth and how has it behaved" — nothing wider.

**This repo must not own** personal finance: purchases, cards, budgets, or the
complete financial picture belong to In Unity (E3/A5). It must not grow a
consumer-shaped endpoint — symbols in, prices-with-currency-and-staleness out.
Both are enforced by `./mvnw verify`, not merely requested.

## One command

```
./mvnw verify
```

Compiles, runs every test including the guardrails in
`src/test/.../guardrails/`. **It is the checklist.** There is no other checklist.
Also: `./mvnw spring-boot:run`, and `./mvnw -Pdemo spring-boot:run` — demo mode
needs no PostgreSQL and no provider key, and that is a product promise.

## Read when you are…

| File | …doing this |
|---|---|
| [`quote-path.md`](docs/policies/quote-path.md) | touching quotes, staleness, the sweep, or fan-out |
| [`providers.md`](docs/policies/providers.md) | adding or changing a provider, or touching `price_candle` |
| [`api-keys.md`](docs/policies/api-keys.md) | touching auth or BYOK — consumer keys and provider keys are different things |
| [`exceptions.json`](docs/policies/exceptions.json) | a guardrail is wrong for your task — add a dated entry and keep moving |
| [`ECOSYSTEM.md`](ECOSYSTEM.md) | anything spanning repos |
| [`FLEET.md`](FLEET.md) | recommending which model and effort to run a task at |

**Say daily or latest close, never real-time** (A6). Asserted, not requested.

## Freedom

Anything not named here and not caught by `./mvnw verify` is yours to decide.
Prefer acting and letting the build fail over asking.
Work on a branch and open a PR; it auto-merges when `verify` is green.
```

- [ ] **Step 4: Shrink `CLAUDE.md`**

Replace `CLAUDE.md` entirely with:

```markdown
@AGENTS.md
```

- [ ] **Step 5: Verify and measure**

Run: `./mvnw --batch-mode test -Dtest=RouterBudgetTest` — expected 3 tests pass.

```bash
node -e "const fs=require('fs');const s=['AGENTS.md','CLAUDE.md'].map(f=>fs.readFileSync(f,'utf8').length).reduce((a,b)=>a+b);console.log('chars',s,'~tokens',Math.round(s/4))"
```

Expected ≤600 tokens, down from 2,385. Record the number in the commit message.

- [ ] **Step 6: Confirm nothing was silently dropped**

```bash
git show HEAD~1:CLAUDE.md > /tmp/old-claude-md.md
```

Read it bullet by bullet against the tests and policy files. Anything homeless goes into the right policy file before committing.

- [ ] **Step 7: Commit**

```bash
git add AGENTS.md CLAUDE.md src/test/java/com/zubairmuwwakil/marketdata/guardrails/RouterBudgetTest.java
git commit -m "feat(agents): AGENTS.md becomes the canonical router

This repo had no AGENTS.md at all, so Codex, Gemini and Copilot saw none of its
ownership boundaries — while CLAUDE.md spent ~2,385 tokens per turn restating
incidents that belong in runbooks. A test holds the budget so it cannot creep back."
```

---

### Task 10: `.claude/settings.json` and repo-local skills

**Files:**
- Create: `.claude/settings.json`
- Create: `.claude/skills/add-a-check/SKILL.md`
- Create: `.claude/skills/add-a-provider/SKILL.md`
- Create: `.claude/skills/quote-path-change/SKILL.md`

- [ ] **Step 1: Check in the allowlist**

Create `.claude/settings.json`:

```json
{
  "permissions": {
    "allow": [
      "Bash(./mvnw --batch-mode verify)",
      "Bash(./mvnw --batch-mode test:*)",
      "Bash(./mvnw --batch-mode -Pdemo test)",
      "Bash(./mvnw --batch-mode compile)",
      "Bash(git status:*)",
      "Bash(git diff:*)",
      "Bash(git log:*)"
    ]
  }
}
```

Verification commands only. Nothing that writes to a remote or mutates a database.

- [ ] **Step 2: Write `add-a-check`**

Create `.claude/skills/add-a-check/SKILL.md`:

```markdown
---
name: add-a-check
description: Use when adding a guardrail to this repo, compiling a rule into an automated test, or when told a rule should be enforced rather than documented.
---

# Adding a guardrail

A check is where a rule goes **so it can stop being said**. If adding it does not let
you delete prose, reconsider whether it is worth adding.

Guardrails here are **plain JUnit 5 tests in
`src/test/java/com/zubairmuwwakil/marketdata/guardrails/`**, not ArchUnit. The
invariants worth enforcing in this repo are about source text — SQL literals,
user-facing copy, endpoint shapes — which do not survive compilation in a form worth
asserting against. They run in the existing surefire pass, so a new check is
triggered the moment it exists.

## Non-negotiables

1. **Every check has its own failing-first run.** Write it, watch it fail against the
   real tree or a fixture, then make it pass.
2. **It must retire specific prose.** Name what you deleted in the commit message.
3. **Do not exempt your way to green.** A registry entry is for a rule that is wrong
   for *this task*, not for a rule you find inconvenient.

## Recipe

1. Add `src/test/.../guardrails/<Rule>Test.java`. Use `SourceScanner.mainSources()`.
2. Assert with a message that says what to do, not just what failed.
3. Honour exemptions via `ExceptionRegistry.pathsFor("<check-id>")`.
4. Delete the prose it replaces from `AGENTS.md` or a policy file.
5. `./mvnw verify`, then commit everything together.

## When a guardrail is wrong for your task

Add an entry to [`docs/policies/exceptions.json`](../../../docs/policies/exceptions.json)
with `id`, `check`, `path`, `why`, `owner`, `reviewDate`. `ExceptionRegistryTest`
fails once it expires, so it cannot become permanent by accident.
```

- [ ] **Step 3: Write `add-a-provider`**

Create `.claude/skills/add-a-provider/SKILL.md`:

```markdown
---
name: add-a-provider
description: Use when adding or changing a market-data provider, touching provider routing, quotas, or the MarketDataProviderRegistry.
---

# Adding a market-data provider

Read [`docs/policies/providers.md`](../../../docs/policies/providers.md) first.

## Rules

- **Register, do not inject.** Resolve through `MarketDataProviderRegistry` by
  `(AssetClass, capability)`. `ProviderResolutionTest` fails on direct injection.
- **Routing is by purpose.** Sanctioned/quota-limited sources serve the curated
  watchlist; unsanctioned/high-headroom sources serve dynamic per-user symbols. Both
  are daily closes — never justify a split by latency.
- **A price carries a currency or it carries nothing.** Never default to USD. Binance
  quotes USDT, not USD.
- **Discard candles after `expectedSession`.** A session still trading is not a close,
  and a candle dated today is never re-fetched today, so accepting one freezes an
  intraday price as the day's close.
- **No credential storage.** A provider key rides one request. See
  [`api-keys.md`](../../../docs/policies/api-keys.md).

## Steps

1. Implement `MarketDataProvider` (and `LatestQuoteProvider` if it serves quotes).
2. Register it in `MarketDataProviderRegistry` under its `(AssetClass, capability)`.
3. Add a provider test beside the existing ones in `service/ingestion/`.
4. Wire it into the **demo profile** too, or the standalone story regresses — the
   `demo profile` CI job takes no services and no secrets on purpose.
5. `./mvnw verify` and `./mvnw -Pdemo test`.
```

- [ ] **Step 4: Write `quote-path-change`**

Create `.claude/skills/quote-path-change/SKILL.md`:

```markdown
---
name: quote-path-change
description: Use when changing QuoteService, the quote sweep, fan-out deadlines, staleness rules, or anything that decides whether a quote is FRESH.
---

# Changing the quote path

Read [`docs/policies/quote-path.md`](../../../docs/policies/quote-path.md) first — its
five rules were each bought with an incident.

## The traps, in short

- **Deadlines are sized for a cold start (45s).** `ProviderPropertiesTest` fails if
  lowered. A sweep that gives up early returns worse data, not faster data.
- **`@Scheduled` is not a scheduler** on a host that spins down.
  `POST /api/v1/admin/quote-sweep` is the load-bearing trigger.
- **The sweep force-refreshes.** A sweep that consults the cache is a no-op exactly
  when it matters, because a wrong candle on the right date passes the miss test.
- **Every non-FRESH quote carries a `CAUSE_*`.** The consumer reads that vocabulary;
  changing it is a cross-repo contract change.
- **Fail closed.** `UNAVAILABLE` with a null price, never a fabricated one.

## Steps

1. Change the code and the test that pins the behaviour, in the same commit.
2. `./mvnw --batch-mode test -Dtest='Quote*Test,ProviderPropertiesTest'`
3. `./mvnw verify` and `./mvnw -Pdemo test`.
4. If a `CAUSE_*` value changed, say so explicitly in the commit — In Unity's
   alerting reads it.
```

- [ ] **Step 5: Verify and commit**

Run: `./mvnw --batch-mode verify` — expected BUILD SUCCESS.

```bash
git add .claude/
git commit -m "feat(agents): permission allowlist and three repo-local skills

The on-demand home for procedure that would otherwise sit in the router:
add-a-check, add-a-provider, quote-path-change. add-a-check records why guardrails
here are plain JUnit rather than ArchUnit."
```

---

### Task 11: PR gating

**Files:**
- GitHub settings (via `gh api`)

- [ ] **Step 1: Confirm the exact check names GitHub sees**

```bash
git push
gh api repos/zubairmuwwakil/marketdata/commits/main/check-runs -q '.check_runs[].name'
```

Required contexts must match character for character.

- [ ] **Step 2: Require them**

```bash
gh api -X PUT repos/zubairmuwwakil/marketdata/branches/main/protection \
  -H "Accept: application/vnd.github+json" \
  -f 'required_status_checks[strict]=true' \
  -f 'required_status_checks[contexts][]=<the CI job name from Step 1>' \
  -f 'required_status_checks[contexts][]=demo profile (no Postgres, no provider key)' \
  -f 'enforce_admins=false' \
  -f 'required_pull_request_reviews[required_approving_review_count]=0' \
  -F 'restrictions=null'
gh api -X PATCH repos/zubairmuwwakil/marketdata -f allow_auto_merge=true -f delete_branch_on_merge=true
```

`required_approving_review_count=0` is deliberate: a solo developer cannot approve
their own PR, and the checks are the review.

- [ ] **Step 3: Prove the gate blocks**

```bash
git switch -c test/gating-proof
printf '\n// deliberate\nclass Broken { int x = "not an int"; }\n' >> src/main/java/com/zubairmuwwakil/marketdata/repository/DatabaseDialect.java
git commit -am "test: deliberate compile error to prove the gate blocks"
git push -u origin test/gating-proof
gh pr create --title "Prove the gate blocks" --body "Deliberate compile error. Expect verify to fail and merge to be blocked."
```

Wait for CI. Expected: the verify job **fails** and the PR reports merging is blocked.

- [ ] **Step 4: Clean up**

```bash
gh pr close --delete-branch
git switch main
git branch -D test/gating-proof
```

- [ ] **Step 5: Update the spec's §3 to record the ArchUnit reversal**

In `../MoneyTalks/docs/superpowers/specs/2026-08-28-ai-native-repos-design.md`, replace the claim that marketdata "gets ArchUnit tests inside the existing surefire run" with the decision recorded at the top of this plan, and note that M3–M5 should choose their check idiom the same way — by reading the invariants against the code first.

```bash
cd ../MoneyTalks
git add docs/superpowers/specs/2026-08-28-ai-native-repos-design.md
git commit -m "docs(specs): marketdata guardrails are plain JUnit, not ArchUnit

Written into the spec before the invariants were read against the code. Only one
of eight is architectural; the rest are source-text rules ArchUnit cannot see."
git push
```

---

## Self-Review

**Spec coverage.** §1 canonical AGENTS.md → Task 9. §2 router contract → Task 9.
§3 invariant ledger → Tasks 3–7 (upsert dialect, provider resolution, service
boundary, honesty copy, demo profile), **with the ArchUnit claim reversed and Task 11
Step 5 correcting the spec**. §4 de-ceremony and freedom clause → Tasks 1 and 9.
§5 PR flow, tiers, exception registry → Tasks 2 and 11. §6 universals → Task 10;
**`REPO_MAP.md` is deliberately omitted** — this repo has a conventional Maven layout
with a single `docs/` tree and no `scripts/` sprawl, so the artifact would guard
nothing. §7 cross-repo freshness and §8 FLEET.md → M6 and M4 respectively; Task 9's
router links `FLEET.md` forward. §9/§10 verification depth and cold-start → Task 7's
demo job; marketdata has no `.env.example` (config lives in `application.yml` and the
Render dashboard), so there is no env-drift check to add.

**Known forward reference.** Task 9's router links `FLEET.md`, created in M4. The link
404s until then — intentional, so nobody "fixes" it by deleting the row.

**Placeholder scan.** One deliberate blank: Task 11 Step 2's required-context name is
filled from Step 1's output, because GitHub reports job names as rendered, and
guessing it would produce a protection rule that silently matches nothing.

**Type consistency.** `SourceScanner.mainSources/read/relative`, `ExceptionRegistry.
load/expired/pathsFor`, and the record `ExceptionRegistry.Entry(id, check, path, why,
owner, reviewDate)` are each defined once (Tasks 2–3) and referenced under those exact
names in Tasks 4, 5, 6 and 9.

**Ordering check against M1's failure.** Task 1 establishes the CI trigger before any
guardrail exists, so every later check is triggered on arrival. This is the specific
defect M1's plan shipped with and its executor had to fix first.
