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

## Time

All timestamps use UTC ISO-8601 format.

Example:

2026-06-23T10:15:30Z
