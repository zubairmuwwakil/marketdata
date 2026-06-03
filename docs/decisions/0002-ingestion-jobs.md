# ADR 0002: Idempotent Ingestion Runs

## Status

Accepted

## Context

Market data ingestion needs to handle retries, backfills, and partial failures.

## Decision

Model runs explicitly in `pipeline_run` with:

- idempotency key
- run type (daily/backfill)
- status + retry count
- timestamps for auditing

## Consequences

- Safe retries without duplication
- Better operational observability
