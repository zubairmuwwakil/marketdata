# Runbook

## Services

- API: Spring Boot (port 8080)
- DB: PostgreSQL

## Health

- `GET /api/v1/health`
- `GET /actuator/health` (ADMIN role for details)

## Metrics

- `GET /actuator/prometheus` (ADMIN role)

## Tracing

- OTLP exporter uses `OTEL_EXPORTER_OTLP_ENDPOINT`

## Logs

- JSON logs in `logs/marketdata.log`

## Common Issues

### Ingestion fails with 429
- Alpha Vantage quota exhausted. Rotate key or wait for reset.

### No data returned for symbol
- Ensure symbol is in watchlist and ingestion run succeeded.

### Flyway migration error
- Check existing schema vs migration history. In dev, reset DB if needed.

## Backfill

- Use `POST /api/v1/ingestion/backfill` with date range.
