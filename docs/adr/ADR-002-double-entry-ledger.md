# ADR-002: Use a Double-Entry Ledger

## Status
Accepted

## Context

Wallet balances and payment records must remain explainable, auditable, and recoverable after failures.

A single mutable balance column is insufficient because it cannot fully explain historical money movement.

## Decision

Every completed financial movement will create a balanced journal entry containing immutable debit and credit postings.

The total debit amount must equal the total credit amount for every journal entry.

## Consequences

Benefits:
- Strong financial traceability.
- Easier reconciliation.
- Safer reversals through compensating entries.
- Clear audit history.

Trade-offs:
- More tables and more complex domain logic.
- Engineers must understand accounting concepts.
