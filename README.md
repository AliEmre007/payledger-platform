# PayLedger

PayLedger is a portfolio-grade simulated digital-wallet and merchant-payment
platform. It is intentionally not a real-money processor: it must not collect
real card data, real KYC documents, production secrets, or customer credentials.

The project demonstrates a Spring Boot modular monolith with PostgreSQL,
Flyway, Keycloak-compatible JWT security, immutable double-entry accounting,
wallet holds, merchant payments, settlement, reconciliation, operations
workflows, audit/outbox foundations, a TypeScript frontend, and containerized
deployment assets.

## What It Shows

- Authenticated customer wallet APIs with server-side ownership checks.
- Mock KYC and wallet lifecycle controls enforced before money movement.
- Ledger-backed wallet transfers with idempotency and source-account locking.
- Available-balance holds for payment authorization.
- Capture, refund, merchant settlement, and reconciliation workflows.
- Operations APIs for KYC, wallet, merchant, payment, settlement, and audit
  review.
- Append-only audit events and transactional outbox records.
- Actuator health and Prometheus-compatible business metrics.
- CI for backend tests, frontend build/test/audit, and backend container build.

## Repository Layout

```text
backend/                  Spring Boot API, Java 21, Maven wrapper
frontend/                 TypeScript/Vite PayLedger UI
infra/compose/            Local and production-oriented Docker Compose files
docs/                     Architecture, API, threat model, ADRs, runbooks
.github/workflows/ci.yml  Backend, frontend, and container CI
```

## Local Development

Start the local infrastructure:

```bash
docker compose -f infra/compose/compose.yaml up -d
```

Run the backend test suite:

```bash
cd backend
./mvnw test
```

Run the frontend:

```bash
cd frontend
npm ci
npm run dev
```

The API listens on `18080`, local Keycloak on `18081`, and local PostgreSQL on
host port `55433`.

## Production-Style Container Check

Build the backend image:

```bash
docker build -t payledger-api:local backend
```

Review the production Compose topology:

```bash
docker compose --env-file infra/compose/production.env.example \
  -f infra/compose/compose.prod.yaml config
```

The production Compose path runs Flyway in the one-shot `backend-migrate`
container and starts the API containers with Flyway disabled.

## Demo Path

Use [docs/demo-workflow.md](docs/demo-workflow.md) for a guided walkthrough.
The scripted scenario covers customer onboarding, KYC approval, wallet funding,
transfer, payment authorization, capture, refund, settlement, reconciliation
discrepancy, and operations review.

## Core Documentation

- [Architecture](docs/architecture.md)
- [API guide](docs/api-guide.md)
- [API standards](docs/api-standards.md)
- [Data model](docs/data-model.md)
- [Development workflow](docs/development-workflow.md)
- [Deployment runbook](docs/deployment-runbook.md)
- [Threat model](docs/threat-model.md)
- [Domain glossary](docs/domain-glossary.md)
- [ADR index](docs/adr/README.md)

## Safety Boundaries

- Money is persisted as integer minor units, never floating point.
- Wallet balances are derived from immutable ledger postings.
- Money movement creates balanced double-entry journals.
- Financial corrections use reversal/correction entries, not posting edits.
- External or retriable money-moving commands are idempotent.
- Customer ownership is derived from JWT `sub` through `customer_identities`.
- Development credentials are placeholders and must not be used in production.
