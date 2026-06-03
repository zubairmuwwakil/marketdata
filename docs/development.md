# Development Guide

## Setup

1. Install Java 21 and Docker.
2. Start PostgreSQL via Docker Compose:

```bash
docker-compose up -d
```

3. Export env vars (or edit application.yml):

- `ALPHAVANTAGE_API_KEY`
- `MARKETDATA_ADMIN_KEY`
- `MARKETDATA_USER_KEY`

4. Run the app:

```bash
./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
```

Tests use Testcontainers (Docker required).

## Linting

No custom lint rules yet. Use your IDE defaults and standard Java formatting.
