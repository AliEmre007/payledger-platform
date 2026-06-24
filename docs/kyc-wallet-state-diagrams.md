# KYC and Wallet State Diagrams

## KYC Review

```mermaid
stateDiagram-v2
    [*] --> NOT_STARTED
    NOT_STARTED --> PENDING: submit for review
    PENDING --> APPROVED: approve
    PENDING --> REJECTED: reject
    REJECTED --> PENDING: resubmit for review
    APPROVED --> EXPIRED: expire verification
    EXPIRED --> PENDING: resubmit for review
```

Notes:

- `PENDING` is the stored enum value for pending review.
- `APPROVED` is the stored enum value for verified.
- SPRINT-09 implements submit, approve, and reject operations. Expiry is
  documented as an existing enum state, but no automated expiry workflow is
  introduced in this sprint.

## Wallet Lifecycle

```mermaid
stateDiagram-v2
    [*] --> ACTIVE
    ACTIVE --> FROZEN: freeze
    FROZEN --> ACTIVE: unfreeze
    ACTIVE --> CLOSED: close if ledger balance is zero
    FROZEN --> CLOSED: close if ledger balance is zero
```

Notes:

- `CLOSED` is terminal.
- Customer-initiated transfers require both wallets to be `ACTIVE`.
- Freeze, unfreeze, and close are operations-only actions, require a reason,
  and write audit and lifecycle history rows.
