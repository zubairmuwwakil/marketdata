# Repository Structure

```
.github/workflows/         CI pipeline
/docs/                      Architecture, runbook, ADRs
/src/main/java/             Spring Boot source
/src/main/resources/        config, migrations, static UI
/src/test/java/             integration tests
Dockerfile                  container build
Docker-compose.yml          local stack
README.md                   project overview
```

## Key Paths

- `src/main/resources/db/migration` — Flyway migrations
- `src/main/resources/static` — UI pages
- `src/main/java/.../controller` — REST endpoints
- `src/main/java/.../service` — business logic
