# PayLedger Deployment Runbook

## Purpose

This runbook describes the SPRINT-21 deployable topology for the simulated
PayLedger platform. It is suitable for local production-like validation and as
a template for a managed environment. It does not contain production secrets.

## Artifacts

- Backend image: built from `backend/Dockerfile`.
- Production Compose topology: `infra/compose/compose.prod.yaml`.
- Environment template: `infra/compose/production.env.example`.
- Prometheus scrape config: `infra/compose/prometheus/prometheus.yml`.
- Reverse proxy template: `infra/compose/nginx/payledger.conf`.

## Backend Container

The backend Dockerfile is multi-stage:

1. Build stage uses Java 21 JDK and Maven wrapper.
2. Runtime stage uses Java 21 JRE.
3. Runtime user is non-root.
4. The container exposes port `18080`.

Build locally:

```bash
docker build -t payledger-api:local backend
```

## Environment Configuration

Copy the template and fill in environment-specific values:

```bash
cp infra/compose/production.env.example infra/compose/.env
```

Required values:

- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `KEYCLOAK_ISSUER_URI`
- `KEYCLOAK_AUDIENCE`

Do not commit real `.env` files, passwords, signing keys, TLS private keys, or
customer data.

## Migration Discipline

Production-like Compose uses a single-writer migration service:

```text
postgres -> backend-migrate -> backend
```

`backend-migrate` starts the application with web mode disabled and Flyway
enabled. It exits after the schema is migrated.

`backend` starts only after `backend-migrate` completes successfully. The
backend service has `SPRING_FLYWAY_ENABLED=false` and
`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, so application replicas validate the
schema but do not compete to migrate it.

For a managed deployment, preserve the same rule: exactly one job or release
step owns Flyway migration before application replicas roll forward.

## Start Production-Like Compose

From the repository root:

```bash
docker compose \
  --env-file infra/compose/.env \
  -f infra/compose/compose.prod.yaml \
  up --build -d postgres backend-migrate backend
```

Health endpoint:

```bash
curl -fsS http://localhost:18080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Metrics

Start with Prometheus:

```bash
docker compose \
  --env-file infra/compose/.env \
  -f infra/compose/compose.prod.yaml \
  --profile metrics \
  up --build -d
```

Prometheus listens on `PROMETHEUS_PUBLISHED_PORT`, default `19090`.

The scrape target is:

```text
backend:18080/actuator/prometheus
```

Metrics must not include customer IDs, wallet IDs, bearer tokens, secrets, or
raw provider payloads.

The backend keeps `/actuator/prometheus` authenticated by default. Set
`PAYLEDGER_MANAGEMENT_PROMETHEUS_PUBLIC=true` only when the endpoint is
reachable exclusively from a trusted internal scrape network, reverse proxy
policy, or platform security group. Do not expose it directly to the public
internet.

## Reverse Proxy And TLS

The `edge` profile starts Nginx as a TLS-terminating reverse proxy:

```bash
docker compose \
  --env-file infra/compose/.env \
  -f infra/compose/compose.prod.yaml \
  --profile edge \
  up --build -d
```

Set:

- `TLS_CERTIFICATE_PATH`
- `TLS_PRIVATE_KEY_PATH`
- `HTTPS_PUBLISHED_PORT`

Use real certificates from an approved certificate manager in a deployed
environment. Do not commit TLS private keys. The application receives
`X-Forwarded-*` headers from the proxy.

## Keycloak External URL

`KEYCLOAK_ISSUER_URI` must be the externally reachable issuer URL used in JWTs,
for example:

```text
https://identity.example.com/realms/payledger
```

The backend validates tokens against this issuer and the configured audience.
Do not use browser password grants. Browser applications use Authorization Code
Flow with PKCE.

## CI

GitHub Actions now runs:

- backend Maven test suite;
- frontend install, production dependency audit, build, and tests;
- backend Docker image build.

The CI container build does not push images. Publishing must be added later with
explicit registry credentials and image signing policy.

## Production Configuration Checklist

- Generate unique database credentials.
- Set `POSTGRES_PASSWORD` from a secret manager.
- Set `KEYCLOAK_ISSUER_URI` to the real external realm issuer.
- Keep `SPRING_FLYWAY_ENABLED=false` for application replicas.
- Run exactly one migration job before backend replicas start.
- Keep `spring.jpa.hibernate.ddl-auto=validate` for backend replicas.
- Configure TLS termination at the reverse proxy or platform ingress.
- Protect metrics endpoints at the network boundary.
- If Prometheus scrapes without a JWT, set
  `PAYLEDGER_MANAGEMENT_PROMETHEUS_PUBLIC=true` only on an internal network.
- Configure backup and restore for PostgreSQL before storing any valuable data.
- Confirm logs and metrics do not contain bearer tokens, payment payloads,
  customer secrets, or real KYC documents.
- Keep PayLedger in simulation mode; do not collect real card data or real KYC
  documents.
