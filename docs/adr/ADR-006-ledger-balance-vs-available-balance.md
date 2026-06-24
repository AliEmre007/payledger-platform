# ADR-006: Separate Ledger Balance From Available Balance

## Status
Accepted

## Context

PayLedger ledger postings are immutable and remain the financial source of
truth. Payment authorization needs a way to reserve funds before a future
capture or release decision, but a reservation is not money movement and must
not be represented as a journal posting.

Without holds, transfer sufficient-funds checks could compare directly against
the postings-derived balance. Once funds can be reserved, that raw ledger
balance can overstate what a customer may spend.

## Decision

PayLedger will track reservations in `funds_holds`.

Balances are defined as:

- `ledgerBalanceMinor`: balance derived from immutable ledger postings.
- `heldAmountMinor`: sum of active funds holds for the wallet.
- `availableBalanceMinor`: `ledgerBalanceMinor - heldAmountMinor`.

Creating, capturing, and releasing holds locks the wallet ledger account before
checking or changing reservation state. Transfers use the same source-account
lock and compare against available balance, not raw ledger balance.

Holds have explicit statuses: `ACTIVE`, `CAPTURED`, `RELEASED`, and `EXPIRED`.
Only `ACTIVE` holds reduce available balance. Capturing or releasing an already
captured or released hold is safe when the requested terminal action matches
the current terminal state.

## Consequences

Benefits:
- Ledger history remains immutable and limited to real money movement.
- Customers cannot spend funds that are already reserved.
- Payment authorization can be added without changing historical ledger
  semantics.
- Concurrent outgoing commands serialize on the wallet ledger account.

Trade-offs:
- Balance reads must explain both ledger and available balance.
- Holds require their own lifecycle, idempotency, and cleanup rules.
- Statements should not present holds as ledger postings; they can be shown as
  a separate reserved/pending summary.
