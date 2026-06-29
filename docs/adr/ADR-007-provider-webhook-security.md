# ADR-007: Secure Provider Webhooks With HMAC and an Inbox

## Status
Accepted

## Context

PayLedger needs to model unreliable payment providers without integrating real
payment rails. Provider callbacks can be retried, duplicated, delayed, or
delivered before the internal transaction is known.

Webhook endpoints cannot rely on customer JWT authentication because external
providers are not PayLedger users. They still need a strong authenticity check,
idempotent processing, and a durable record of what was accepted.

## Decision

PayLedger will authenticate fake provider webhooks with HMAC-SHA256 over the
raw request body. The shared secret is environment configured, with only a
development fallback for local simulation.

The webhook endpoint is excluded from JWT requirements, but the HMAC signature
is required before parsing or persisting a callback. Invalid signatures return
`401`.

Accepted callbacks are stored in `provider_webhook_events` as an inbox record.
The table stores provider metadata, event identifiers, SHA-256 hashes of the
payload and signature, processing status, and limited JSON metadata. It does
not store raw secrets, bearer tokens, or full callback payloads.

`provider_webhook_events` enforces uniqueness on `(provider_name,
provider_event_id)`. Duplicate deliveries return the existing inbox result and
do not repeat capture or any other money movement.

Provider success events capture the payment through the existing
`PaymentIntentService` and its idempotent ledger-backed workflow. Provider
failure events mark the provider transaction failed and leave the authorized
payment and active hold recoverable for a later release or retry workflow.
Unknown or out-of-order provider transactions are recorded as ignored without
trusting the claimed payment intent ID.

## Consequences

Benefits:
- Provider callbacks have an authentication boundary without requiring JWTs.
- Duplicate webhook retries cannot duplicate ledger movement.
- Raw webhook bodies and signatures are not persisted.
- Unknown provider callbacks are durable for review without linking to
  unverified payment intent IDs.

Trade-offs:
- The fake provider secret must be rotated and configured differently outside
  local development.
- Ignored unknown callbacks are not automatically replayed in this sprint.
- Webhook ordering is handled conservatively; richer retry orchestration is
  deferred to settlement and reconciliation work.
