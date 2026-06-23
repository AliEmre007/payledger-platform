# ADR-001: Start with a Modular Monolith

## Status
Accepted

## Context

PayLedger contains multiple fintech domains, including identity, wallets, ledger, payments, settlements, reconciliation, and risk controls.

Starting with microservices would create unnecessary operational complexity before the financial domain model is stable.

## Decision

PayLedger will begin as one deployable Spring Boot application with strongly separated internal modules.

## Consequences

Benefits:
- Faster development and debugging.
- Single transactional database boundary for the financial core.
- Lower deployment and observability complexity.
- Easier integration testing.

Trade-offs:
- Modules must remain disciplined to avoid becoming tightly coupled.
- Future service extraction requires deliberate interface design.
