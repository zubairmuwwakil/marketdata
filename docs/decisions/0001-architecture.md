# ADR 0001: Modular Spring Boot Architecture

## Status

Accepted

## Context

We need a maintainable service with clear separation of concerns: ingestion, analytics, data quality, and API layers.

## Decision

Use Spring Boot with a layered architecture:

- controller → service → repository
- domain entities for core data
- external clients for provider APIs

## Consequences

- Clear boundaries for team ownership
- Easier testing and scaling by module
