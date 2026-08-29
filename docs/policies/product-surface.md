# Standalone product surface

**Read when:** adding a capability, endpoint, dashboard, or demo-profile wiring.

MarketLens is its own product. In Unity is its first consumer, not its purpose. A
capability is complete only when it is available through all four public surfaces:

1. An API protected by MarketLens consumer-key authentication.
2. Accurate OpenAPI documentation.
3. A dashboard page or affordance that makes the capability usable without a custom
   client.
4. Demo-profile support using H2 and no provider key.

Do not ship a production-only capability and call demo support follow-up work. Demo
mode is a product surface, not a test fixture. Likewise, do not add a private,
consumer-shaped endpoint for In Unity; expose a general MarketLens capability that
can serve any client.

Run `./mvnw --batch-mode verify` and `./mvnw --batch-mode -Pdemo test`. For a new
interactive surface, also start `./mvnw -Pdemo spring-boot:run` and exercise its API
and dashboard path before considering it complete.
