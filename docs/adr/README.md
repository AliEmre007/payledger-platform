# Architecture Decision Records

This directory contains the durable architecture decisions for PayLedger.

| ADR | Decision |
|---|---|
| [ADR-001](ADR-001-modular-monolith.md) | Build PayLedger as a modular monolith first. |
| [ADR-002](ADR-002-double-entry-ledger.md) | Use double-entry accounting for all money movement. |
| [ADR-003](ADR-003-money-representation.md) | Store money as integer minor units. |
| [ADR-004](ADR-004-idempotent-money-commands.md) | Require idempotency for retriable money-moving commands. |
| [ADR-005](ADR-005-transactional-outbox.md) | Commit outbox events in the same transaction as business changes. |
| [ADR-006](ADR-006-ledger-balance-vs-available-balance.md) | Keep ledger balance distinct from available balance. |
| [ADR-007](ADR-007-provider-webhook-security.md) | Treat provider webhooks as signed, idempotent integration input. |
| [ADR-008](ADR-008-deterministic-risk-controls.md) | Use deterministic risk controls for the portfolio simulation. |

New decisions should be added as the next sequential ADR and linked here.
