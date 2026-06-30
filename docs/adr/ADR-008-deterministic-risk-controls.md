# ADR-008: Deterministic Risk Controls Before Money Movement

## Status
Accepted

## Context

PayLedger needs an explicit risk decision point before customer-initiated
money movement. The current platform is a simulation, so the first risk model
must be deterministic, understandable, and testable without external vendors or
machine-learning dependencies.

Risk denials must be auditable, but customer-facing responses must not reveal
policy thresholds or detailed rule internals.

## Decision

PayLedger evaluates deterministic risk controls before creating financial
postings or funds holds.

The initial policies are:
- maximum amount per transfer or payment authorization;
- maximum daily outgoing amount per customer and currency;
- maximum daily outgoing command count per customer and currency;
- blocked customer, wallet, and merchant statuses.

The risk service returns a structured allow/deny decision with a stable reason
code. Calling workflows map denials to `RISK_DENIED` for customers and use a
generic message.

Risk denial audit events are written in a separate transaction so a rejected
money movement can roll back without losing the denial record. Audit metadata
contains the action, reason code, resource identifiers, amount, and currency,
but no secrets, bearer tokens, passwords, or full policy internals.

## Consequences

Benefits:
- Transfers and payment authorizations share the same velocity controls.
- Denials are durable even when no business record, hold, or journal is created.
- Future manual-review or provider-backed risk decisions can replace or extend
  the deterministic service boundary.

Trade-offs:
- Policy thresholds are code-configured in this sprint rather than externally
  configurable.
- Daily velocity uses the existing database history and UTC calendar days.
- Denial audit records can outlive rolled-back request transactions by design.
