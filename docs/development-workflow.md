# PayLedger Development Workflow

## Backend Tests

Run the backend test suite from the project root:

```bash
cd backend
./mvnw test
```

The test suite uses Testcontainers PostgreSQL through the Spring `test`
profile. Docker must be available, but the local Compose stack does not need
to be running. Tests do not require a manually started PostgreSQL instance on
port `55433` or a live Keycloak container.

MockMvc API tests use Spring Security test JWT support. They create request
level mocked JWTs and seed only the PayLedger customer identity records needed
for each test.

## Local Services

Use the local Compose stack for manual application testing:

```bash
docker compose -f infra/compose/compose.yaml up -d
```

The local API database is published on `localhost:55433`, and local Keycloak is
published on `localhost:18081`. These services are development-only and are not
required for `./mvnw test`.

## CI

GitHub Actions runs the backend Maven test suite on Java 21. The CI runner must
provide Docker so Testcontainers can start PostgreSQL during the test job.
