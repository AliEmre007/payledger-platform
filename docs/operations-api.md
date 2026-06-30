# Operations API Guide

SPRINT-17 defines controlled operations workflows for internal support and
review users. These APIs are not customer self-service surfaces.

All operations endpoints are under:

```text
/api/v1/operations
```

## Role Matrix

| Capability | CUSTOMER | OPERATIONS | ADMIN |
|---|---:|---:|---:|
| Read own customer wallet APIs | Yes | No | No |
| KYC review actions | No | Yes | Yes |
| Wallet freeze, unfreeze, close | No | Yes | Yes |
| Merchant onboarding and lifecycle | No | Yes | Yes |
| Payment capture and refund | No | Yes | Yes |
| Settlement batch creation | No | Yes | Yes |
| Settlement reconciliation | No | Yes | Yes |
| Customer identity linking | No | Yes | Yes |
| Operational read views | No | Yes | Yes |

`ROLE_OPERATIONS` and `ROLE_ADMIN` can use operations endpoints. Customer tokens
receive `403 Forbidden`.

## Mutation Rules

Every operations mutation must include:

- an authenticated operator JWT;
- a non-empty human-readable reason;
- an audit event with the operator subject and reason;
- the same transactional accounting guarantees as the underlying workflow.

Examples:

```text
POST /api/v1/operations/customers/{customerId}/kyc/approve
POST /api/v1/operations/wallets/{walletId}/freeze
POST /api/v1/operations/merchants/{merchantId}/suspend
POST /api/v1/operations/payment-intents/{paymentIntentId}/capture
POST /api/v1/operations/settlements
POST /api/v1/operations/settlements/{settlementBatchId}/reconcile
POST /api/v1/operations/customers/{customerId}/identities/keycloak
```

Money-moving operations also require `Idempotency-Key` when they can be retried.

## Operational Reads

Operational reads are paginated and use stable ordering:

```text
ORDER BY created_at DESC, id DESC
```

Supported reads:

```text
GET /api/v1/operations/audit-events
GET /api/v1/operations/customers
GET /api/v1/operations/wallets
GET /api/v1/operations/payment-intents
GET /api/v1/operations/reconciliation-cases
```

Common pagination query parameters:

```text
page=0
size=20
```

`size` is capped at 100.

Filters:

| Endpoint | Filters |
|---|---|
| `/audit-events` | `actionType`, `resourceType` |
| `/customers` | `status`, `kycStatus` |
| `/wallets` | `customerId`, `status` |
| `/payment-intents` | `customerId`, `merchantId`, `status` |
| `/reconciliation-cases` | `merchantId`, `status` |

Operational reads may include customer identifiers and operational metadata.
They must not expose bearer tokens, passwords, raw provider signatures, or
unnecessary real-world PII.
