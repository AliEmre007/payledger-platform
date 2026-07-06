# PayLedger API Standards

## Base Path

All public APIs use:

/api/v1

## Resource Style

Use nouns for resources.

Examples:

GET /api/v1/wallets
GET /api/v1/transfers/{transferId}
POST /api/v1/payment-intents

## Financial Commands

Endpoints that can move money or alter financial state must:

- Use POST, not GET.
- Require an Idempotency-Key request header.
- Return a stable response for repeated use of the same key.
- Create an audit event.
- Apply object-level authorization.
- Use explicit transaction boundaries.

## Authenticated Customer Context

Public customer APIs derive the acting customer from the authenticated JWT.
Clients must not send customer IDs to prove ownership or initiate money
movement. The API reads the JWT `sub` claim and resolves it through the
`customer_identities` mapping.

Customer-owned wallet resources require the resolved customer to own the
wallet:

GET /api/v1/wallets/{walletId}/balance
GET /api/v1/wallets/{walletId}/statement
POST /api/v1/transfers
POST /api/v1/payment-intents

For transfers, the source wallet must belong to the JWT-linked customer. The
destination wallet is an explicit transfer target, but source-wallet ownership
is always enforced server-side.

Customer-initiated money movement requires approved KYC. Stored status
`APPROVED` means verified. Transfers require both source and destination
customers to be approved. Payment-intent authorization requires the paying
customer to be approved.

Transfers require both wallets to be `ACTIVE`; frozen and closed wallets are
rejected with business-rule errors. Payment-intent authorization requires the
source wallet to be `ACTIVE`, owned by the authenticated customer, and in the
same currency as the payment.

Payment-intent authorization reserves available wallet funds with a funds hold.
It does not create a ledger journal or posting until a later capture workflow.
The merchant must be `ACTIVE` and enabled for the payment currency.

Payment capture and refund are operations-only workflows until a merchant-auth
surface exists. Both require an `Idempotency-Key` header. Capture consumes the
active hold and creates the payment ledger journal. Refund creates a separate
compensating journal and never edits the capture journal.

## Risk Controls

Customer-initiated transfers and payment-intent authorizations pass through a
risk decision point before any ledger posting or funds hold is created.

Initial risk controls are deterministic:

- per-command maximum amount;
- daily outgoing amount by customer and currency;
- daily outgoing command count by customer and currency;
- blocked customer, wallet, and merchant statuses.

Risk denials return `RISK_DENIED` with HTTP `422`. The response message is
generic and does not disclose policy thresholds or detailed rule internals.
The internal denial reason code is recorded in audit metadata for operations
review.

## Operations APIs

Operations APIs use:

/api/v1/operations

These endpoints require `ROLE_OPERATIONS`. They are not customer self-service
APIs and must not derive authority from customer ownership.

KYC decisions and wallet lifecycle changes:

- Use POST.
- Require a non-empty reason in the request body.
- Record the operations actor from the JWT `sub` claim.
- Create an audit event.
- Create a dedicated history row containing previous state, next state, actor,
  reason, and timestamp.

Examples:

POST /api/v1/operations/customers/{customerId}/kyc/submit
POST /api/v1/operations/customers/{customerId}/kyc/approve
POST /api/v1/operations/customers/{customerId}/kyc/reject
POST /api/v1/operations/wallets/{walletId}/freeze
POST /api/v1/operations/wallets/{walletId}/unfreeze
POST /api/v1/operations/wallets/{walletId}/close

## IDs

Public identifiers use UUID values.

Sequential database IDs must not be exposed through public APIs.

## Money

Monetary amounts are represented in minor units.

Example:

{
  "amount": 1050,
  "currency": "TRY"
}

This represents 10.50 TRY.

## Error Response Format

Errors will use a consistent JSON structure:

{
  "code": "TRANSFER_INSUFFICIENT_FUNDS",
  "message": "The wallet does not have sufficient available balance.",
  "traceId": "..."
}

Sensitive internal details, stack traces, passwords, tokens, and database errors must never be returned to API clients.

Authentication and ownership errors use stable codes:

- `401 Unauthorized`: missing or invalid bearer token.
- `IDENTITY_NOT_LINKED` with HTTP `403`: the JWT is valid, but its `sub` is not linked to a PayLedger customer.
- `WALLET_ACCESS_DENIED` with HTTP `403`: the linked customer attempted to access or debit another customer's wallet.
- `RISK_DENIED` with HTTP `422`: a transfer or payment authorization was rejected by risk controls.
- `REQUEST_BODY_TOO_LARGE` with HTTP `413`: the request body exceeds the configured API limit.
- `RATE_LIMIT_EXCEEDED` with HTTP `429`: the caller exceeded the local money-moving request limit; the response includes `Retry-After`.
- `MALFORMED_REQUEST_BODY` with HTTP `400`: the JSON body is missing or cannot be parsed into the request contract.
- `INTERNAL_ERROR` with HTTP `500`: an unexpected server failure occurred; the message must not include stack traces or internal implementation details.

## Trace, Metrics, And API Docs

Every response includes `X-Trace-Id`. Callers may supply a UUID `X-Trace-Id`
header to correlate a request across API responses, logs, and audit events.
Invalid or absent trace IDs are replaced by a generated UUID.

Health is available at:

GET /actuator/health

Metrics and Prometheus-format metrics are authenticated:

GET /actuator/metrics
GET /actuator/prometheus

Business metrics must use low-cardinality labels and must not include PII,
wallet IDs, raw provider payloads, bearer tokens, or secrets.

OpenAPI metadata is available at:

GET /api-docs

## Time

All timestamps use UTC ISO-8601 format.

Example:

2026-06-23T10:15:30Z
