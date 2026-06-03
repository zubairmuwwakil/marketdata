# Security

## API Keys

- Keys are configured via env vars (`MARKETDATA_ADMIN_KEY`, `MARKETDATA_USER_KEY`).
- Use `X-API-Key` header for all `/api/**` endpoints.
- Admin routes (`/api/v1/admin/**`, `/actuator/prometheus`) require ADMIN role.

## Key Rotation

- Update env vars and redeploy.
- Keys are not stored in the database.

## Audit Notes

- Request IDs are added to logs for traceability.
- API key fingerprints are logged as `apiKeyId` (hashed prefix).
- Consider adding per-key audit storage if multi-tenant usage grows.
