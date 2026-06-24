# ADR-005: Use Transactional Outbox For Business Events

## Status
Accepted

## Context

PayLedger needs reliable auditability and a safe foundation for future
asynchronous side effects such as notifications, provider simulation,
reconciliation, and operations workflows.

Writing a business record and then publishing an event outside the database
transaction can lose events or publish events for rolled-back work.

## Decision

PayLedger will write audit events and outbox events in the same PostgreSQL
transaction as the business operation.

Audit events are append-only records for business and security review. Outbox
events are append-only pending event intents. The initial outbox foundation does
not introduce Kafka, RabbitMQ, or a polling processor.

The outbox event identifier is stable for the event type and aggregate so
duplicate event intent is rejected by the database.

## Consequences

Benefits:
- Business records, ledger journals, audit events, and outbox events commit or
  roll back together.
- Future asynchronous processors can publish from the database-backed outbox.
- Audit history cannot be changed through normal database operations.

Trade-offs:
- Outbox rows cannot yet transition to processed state; processing lifecycle is
  deferred to a later sprint.
- Workflows must keep event payloads minimal and free of secrets.
- Additional rows are written in business transactions.
