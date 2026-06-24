# PayLedger Architecture

## Architectural Style

PayLedger starts as a modular monolith.

The application is deployed as one Spring Boot service, but its code is organized into separate business modules with explicit responsibilities and boundaries.

This approach keeps the first implementation manageable while preserving a future path to extract independent services when justified by scale, ownership, or reliability needs.

## Initial Modules

- identity: authentication, authorization, roles, sessions.
- customer: customer profile and mock KYC lifecycle.
- wallet: wallet management and customer balances.
- ledger: journal entries, postings, financial invariants.
- transfer: internal wallet-to-wallet money movement.
- payments: merchant payments and payment intents.
- provider: simulated external payment-provider integration.
- settlement: merchant settlement and fees.
- reconciliation: provider statement comparison and mismatch handling.
- risk: transaction limits, flags, account restrictions.
- audit: immutable activity and security audit records.
- notification: asynchronous user and merchant notifications.
- operations: internal operational workflows and administrative controls.

## Source of Truth

PostgreSQL is the transactional source of truth.

The ledger module is the financial source of truth. Wallet balances are derived from, or safely projected from, validated ledger postings.

Redis is not the financial source of truth. It may later support caching, rate limiting, locks, or short-lived workflow state.

## Audit And Outbox

Business workflows write audit and outbox rows in the same PostgreSQL
transaction as the business record and, when applicable, the financial journal.

Audit events are append-only records of customer and platform activity. They
capture action type, actor identity context, resource identity, trace ID, and
limited JSON metadata. Metadata must not include bearer tokens, passwords,
secrets, or unnecessary PII.

Outbox events are append-only pending integration events. They capture a stable
event identifier, event type, aggregate identity, payload, creation time, and
initial processing state. A later sprint will add a processor; this foundation
only guarantees that event intent is committed atomically with the business
operation.

## Local Development Topology

Client
  |
  v
Spring Boot API :18080
  |
  +--> PostgreSQL :55433
  |
  +--> Redis :56379

Kafka, Keycloak, Prometheus, Grafana, and MinIO will be added in controlled phases after the core API and database foundation are stable.

## Database Rules

- Financial tables are changed only through Flyway migrations.
- Hibernate validates mappings but does not modify schema.
- Financial records are append-only where possible.
- Audit and outbox rows are append-only.
- All money-moving operations run inside explicit database transactions.
- Foreign keys, unique constraints, and database checks enforce critical invariants.

## Future Deployment Path

Local Docker Compose
  -> GitHub Actions CI
  -> AWS infrastructure with Terraform
  -> Container registry
  -> Kubernetes or managed container deployment
  -> Observability and alerting
