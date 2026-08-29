---
name: add-a-check
description: Use when adding a MarketLens guardrail or compiling a rule into an automated test instead of documenting it.
---

# Adding a guardrail

A check is where a rule goes so it can stop being said. If it does not retire
specific prose, reconsider whether it is worth adding.

Guardrails are plain JUnit 5 tests in
`src/test/java/com/zubairmuwwakil/marketdata/guardrails/`, not ArchUnit. They
protect source-level invariants such as SQL literals, user-facing copy, and
endpoint shapes, and run in the existing Surefire pass.

## Requirements

1. Write the check first and observe it fail against the real tree or a fixture.
2. Give failures an actionable message.
3. Honour scoped exemptions with `ExceptionRegistry.pathsFor("<check-id>")`.
4. Do not add an exemption just to make a valid rule green.
5. Delete the prose the check replaces and name it in the commit message.

Use `SourceScanner.mainSources()` for source-level checks. Run `./mvnw --batch-mode
verify` before committing.

## Legitimate exemptions

When a guardrail is genuinely wrong for the current task, add a dated entry to
[`docs/policies/exceptions.json`](../../../docs/policies/exceptions.json) with
`id`, `check`, `path`, `why`, `owner`, and `reviewDate`. Expired entries fail the
build.
