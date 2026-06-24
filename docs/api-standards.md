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

For transfers, the source wallet must belong to the JWT-linked customer. The
destination wallet is an explicit transfer target, but source-wallet ownership
is always enforced server-side.

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

## Time

All timestamps use UTC ISO-8601 format.

Example:

2026-06-23T10:15:30Z
