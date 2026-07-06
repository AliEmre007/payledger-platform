# PayLedger API Guide

This guide summarizes the main API workflows. Detailed standards, error
formats, and auth semantics are defined in [api-standards.md](api-standards.md).

## Authentication

Customer endpoints require a JWT bearer token. PayLedger derives the customer
from JWT `sub` through `customer_identities`; clients do not provide customer
IDs to prove ownership.

Operations endpoints require `ROLE_OPERATIONS` and live under:

```text
/api/v1/operations
```

Provider webhooks are public only to the simulated provider contract and use
their own idempotency/signature discipline.

## Customer Wallet Flow

1. Customer identity is linked during controlled onboarding.
2. Customer has a wallet for a currency.
3. Operations approves mock KYC.
4. Customer reads wallet balance or statement.
5. Customer initiates transfer or payment authorization with an
   `Idempotency-Key` header.

Customer-owned endpoints include:

```text
GET  /api/v1/wallets/{walletId}/balance
GET  /api/v1/wallets/{walletId}/statement
POST /api/v1/transfers
POST /api/v1/payment-intents
POST /api/v1/payment-intents/{paymentIntentId}/cancel
```

## Money Movement

Transfers create a completed transfer business record and a balanced ledger
journal immediately.

Payment authorization creates a payment intent and funds hold. It lowers
available balance but does not create a payment ledger journal. Capture creates
the capture journal. Refund creates a separate compensating journal.

Every retriable money command uses `Idempotency-Key`; replay with the same
request returns the original result, while reuse with a different request is a
conflict.

## Operations Workflows

Operations APIs cover:

- KYC submit/approve/reject;
- wallet freeze/unfreeze/close;
- merchant onboard/activate/suspend/close;
- payment capture/refund;
- settlement batch creation;
- settlement reconciliation;
- audit and operational read models.

Each mutation requires an operations reason and records audit history.

## Observability

Health is public:

```text
GET /actuator/health
```

Prometheus metrics are authenticated by default and can be made public only on
an internal scrape network with `PAYLEDGER_MANAGEMENT_PROMETHEUS_PUBLIC=true`.

OpenAPI metadata is available at:

```text
GET /api-docs
```
