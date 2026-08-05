# MarketLens

MarketLens is a Spring Boot market data pipeline and analytics service for equities. It ingests Alpha Vantage daily price data, stores it in PostgreSQL, calculates indicators, tracks ingestion runs, exposes API-key-protected REST endpoints, and includes static operational dashboards for local or demo use.

## What It Does

- Ingests daily and backfilled market data for a configurable watchlist.
- Stores normalized OHLCV candles with Flyway-managed PostgreSQL migrations.
- Calculates RSI, EMA, and MACD technical indicators.
- Tracks pipeline runs, retries, provider quota usage, and malformed rows.
- Supports corporate actions and split-adjusted price queries.
- Provides NYSE trading calendar and early-close awareness.
- Reports data quality issues such as missing days, duplicates, and outliers.
- Secures APIs with API keys, roles, rate limiting, and quota headers.
- Exposes Swagger/OpenAPI docs, health checks, Prometheus metrics, JSON logs, and optional OTLP tracing.
- Ships with static dashboard pages for watchlists, runs, indicators, quality, corporate actions, calendar, API keys, and status.

## Tech Stack

- Java 21
- Spring Boot 4.0.1
- Spring Web, Spring Data JPA, Spring Security, Actuator
- PostgreSQL 16 locally via Docker Compose
- Flyway migrations
- Alpha Vantage market data provider
- Springdoc OpenAPI
- Micrometer, Prometheus, OTLP tracing
- Caffeine cache
- Bucket4j rate limiting
- Testcontainers for integration tests

## Quick Start

### Demo Mode

Use demo mode when you want to see the product without PostgreSQL or an Alpha Vantage key.

```bash
./mvnw -Pdemo spring-boot:run
```

Then open:

- Dashboard: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui`
- Health: `http://localhost:8080/api/v1/health`

Demo mode uses an in-memory H2 database, disables Flyway, seeds realistic data, and auto-loads the demo admin key in browser storage when needed.

Seeded data includes:

- Active watchlist symbols: `MSFT`, `AAPL`, `NVDA`, `SPY`
- An inactive `TSLA` symbol with an intentional data gap for quality checks
- RSI/MACD indicator history
- Corporate actions
- Pipeline runs
- Provider quota usage
- One quarantine example

### Local PostgreSQL Mode

Start the database:

```bash
docker-compose up -d
```

Export local configuration:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/marketdata
export SPRING_DATASOURCE_USERNAME=marketdata
export SPRING_DATASOURCE_PASSWORD=marketdata
export MARKETDATA_ADMIN_KEY=change-me-admin
export MARKETDATA_USER_KEY=change-me-user
export ALPHAVANTAGE_API_KEY=your-alpha-vantage-key
```

Run the service:

```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080`.

## Environment Variables

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | No | `jdbc:postgresql://localhost:5433/marketdata` | JDBC URL for the application datasource. |
| `SPRING_DATASOURCE_USERNAME` | No | `marketdata` | Database username. |
| `SPRING_DATASOURCE_PASSWORD` | No | `marketdata` | Database password. |
| `SPRING_FLYWAY_URL` | No | datasource URL | Optional Flyway-specific JDBC URL. |
| `SPRING_FLYWAY_USER` | No | datasource username | Optional Flyway-specific database user. |
| `SPRING_FLYWAY_PASSWORD` | No | datasource password | Optional Flyway-specific database password. |
| `DATABASE_URL` | No | unset | Render-style PostgreSQL URL. Used when datasource env vars are not set. |
| `MARKETDATA_ADMIN_KEY` | No | `change-me-admin` | Admin API key for protected admin endpoints. |
| `MARKETDATA_USER_KEY` | No | `change-me-user` | User API key for regular API access. |
| `ALPHAVANTAGE_API_KEY` | Yes outside demo | unset | Alpha Vantage provider key. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No | `http://localhost:4317` | OTLP tracing endpoint. |

Do not use the default API keys in shared or production environments.

## UI Pages

All pages are served by the Spring Boot app:

| Page | URL |
| --- | --- |
| Dashboard | `http://localhost:8080/` |
| Watchlist | `http://localhost:8080/watchlist.html` |
| Indicators | `http://localhost:8080/indicators.html` |
| Ingestion runs | `http://localhost:8080/runs.html` |
| Data quality | `http://localhost:8080/quality.html` |
| Corporate actions | `http://localhost:8080/actions.html` |
| Market calendar | `http://localhost:8080/calendar.html` |
| API keys | `http://localhost:8080/keys.html` |
| Status | `http://localhost:8080/status.html` |

The UI sends API requests with the `X-API-Key` header.

## API Authentication

All `/api/**` routes require an API key unless explicitly public.

```http
X-API-Key: <your-key>
```

Configured keys map to roles:

| Role | Access |
| --- | --- |
| `USER` | General `/api/**` access plus provider key validation and quota status. |
| `ADMIN` | User access plus `/api/v1/admin/**` and `/actuator/prometheus`. |

Public endpoints include:

- `/`
- `/*.html`
- `/api/v1/demo/**`
- `/api/v1/health`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/actuator/health/**`
- `/actuator/info/**`

## Core API Endpoints

| Area | Method | Endpoint |
| --- | --- | --- |
| Health | `GET` | `/api/v1/health` |
| Watchlist | `GET` | `/api/v1/watchlist` |
| Watchlist | `PUT` | `/api/v1/watchlist` |
| Ingestion | `POST` | `/api/v1/ingestion/run` |
| Ingestion | `POST` | `/api/v1/ingestion/backfill` |
| Ingestion | `GET` | `/api/v1/ingestion/runs?limit=20` |
| Ingestion | `GET` | `/api/v1/ingestion/runs/latest` |
| Ingestion | `GET` | `/api/v1/ingestion/runs/{id}` |
| Ingestion | `POST` | `/api/v1/ingestion/runs/{id}/retry` |
| Quarantine | `GET` | `/api/v1/ingestion/quarantine?symbol=MSFT&from=2024-01-01&to=2024-12-31&limit=200` |
| Market data | `GET` | `/api/v1/market/summary?active=true` |
| Market data | `GET` | `/api/v1/market/adjusted?symbol=MSFT&from=2024-01-01&to=2024-12-31` |
| Indicators | `GET` | `/api/v1/indicators/{symbol}` |
| Indicators | `GET` | `/api/v1/indicators/{symbol}/{type}` |
| Corporate actions | `GET` | `/api/v1/corporate-actions?symbol=MSFT` |
| Corporate actions | `POST` | `/api/v1/corporate-actions` |
| Calendar | `GET` | `/api/v1/calendar/nyse?from=2026-01-01&to=2026-12-31` |
| Calendar | `GET` | `/api/v1/calendar/nyse/early-closes?from=2026-01-01&to=2026-12-31` |
| Data quality | `GET` | `/api/v1/quality/report?symbol=MSFT&from=2024-01-01&to=2024-12-31` |
| Provider key | `POST` | `/api/v1/admin/api-key` |
| Provider quota | `GET` | `/api/v1/admin/quota` |
| API key admin | `GET` | `/api/v1/admin/keys` |
| API key admin | `POST` | `/api/v1/admin/keys` |
| API key admin | `POST` | `/api/v1/admin/keys/{keyId}/rotate` |
| API key admin | `DELETE` | `/api/v1/admin/keys/{keyId}` |

Ingestion run and backfill requests accept an optional `Idempotency-Key` header.

## Example Requests

Set the active watchlist:

```bash
curl -X PUT http://localhost:8080/api/v1/watchlist \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me-user' \
  -d '{"symbols":["MSFT","AAPL","NVDA","SPY"]}'
```

Run daily ingestion:

```bash
curl -X POST http://localhost:8080/api/v1/ingestion/run \
  -H 'X-API-Key: change-me-admin' \
  -H 'Idempotency-Key: daily-2026-08-05'
```

Backfill a date range:

```bash
curl -X POST http://localhost:8080/api/v1/ingestion/backfill \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: change-me-admin' \
  -H 'Idempotency-Key: backfill-msft-2024' \
  -d '{"symbols":["MSFT"],"from":"2024-01-01","to":"2024-12-31"}'
```

Fetch indicators:

```bash
curl http://localhost:8080/api/v1/indicators/MSFT \
  -H 'X-API-Key: change-me-user'
```

Fetch adjusted prices:

```bash
curl "http://localhost:8080/api/v1/market/adjusted?symbol=MSFT&from=2024-01-01&to=2024-12-31" \
  -H 'X-API-Key: change-me-user'
```

Check provider quota:

```bash
curl http://localhost:8080/api/v1/admin/quota \
  -H 'X-API-Key: change-me-user'
```

## Rate Limits And Quotas

Rate limiting applies to `/api/**` responses and emits:

- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`

Provider quota headers are also included:

- `X-Quota-Limit`
- `X-Quota-Remaining`

MarketLens tracks two quota concepts:

- Per-application-key request quotas for API consumers.
- Alpha Vantage provider quota usage for ingestion calls.

## Database

Flyway migrations live in `src/main/resources/db/migration`.

The schema includes:

- Watchlist symbols
- Price candles partitioned by year
- Technical indicators
- Provider quota usage
- Pipeline runs
- Corporate actions
- Ingestion quarantine entries

If you change migrations during local development, reset the local database or repair the Flyway history before rerunning the app.

## Testing

Run unit and slice tests:

```bash
./mvnw test
```

Run the full verification suite:

```bash
./mvnw verify
```

`verify` packages the jar, runs integration tests, starts a PostgreSQL Testcontainers database, boots the app with a Render-style `DATABASE_URL`, runs Flyway migrations, and checks `/api/v1/health`.

Docker must be running for tests that use Testcontainers.

## Observability

- Health: `/actuator/health`
- Info: `/actuator/info`
- Metrics: `/actuator/metrics`
- Prometheus: `/actuator/prometheus` with an `ADMIN` key
- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui`
- JSON application logs: `logs/marketdata.log`
- Optional OTLP tracing through `OTEL_EXPORTER_OTLP_ENDPOINT`

## Deployment

The repository includes:

- `Dockerfile` for containerized builds.
- `render.yaml` for Render web service plus managed PostgreSQL deployment.

For Render, set `ALPHAVANTAGE_API_KEY`, `MARKETDATA_ADMIN_KEY`, and `MARKETDATA_USER_KEY` as environment variables. The blueprint wires `DATABASE_URL` from the managed database.

## Documentation

Additional docs live under `docs/`:

- [Documentation index](docs/README.md)
- [Architecture](docs/architecture.md)
- [Repository structure](docs/structure.md)
- [Development guide](docs/development.md)
- [Runbook](docs/runbook.md)
- [SLOs and alerts](docs/slo-alerts.md)
- [Security](docs/security.md)
- [Accessibility](docs/accessibility.md)
- [Reference](docs/reference.md)
- [Architecture decisions](docs/decisions/README.md)

## Repository Layout

```text
src/main/java/com/zubairmuwwakil/marketdata
  client/          Alpha Vantage client and external service errors
  config/          Spring, OpenAPI, cache, security, and property configuration
  controller/      REST API controllers
  demo/            Demo profile seed data and demo configuration endpoints
  model/           DTOs and JPA entities
  observability/   Request ID filter
  repository/      Spring Data repositories and custom upsert logic
  resilience/      Simple circuit breaker
  security/        API key auth, quotas, and rate limiting
  service/         Ingestion, indicators, calendar, quality, retention, market data

src/main/resources
  db/migration/    Flyway SQL migrations
  static/          Static dashboard pages
```
