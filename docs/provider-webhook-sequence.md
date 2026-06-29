# Provider Webhook Sequence

This sequence covers the fake provider simulator used by Sprint 14.

```mermaid
sequenceDiagram
    participant Customer
    participant API as PayLedger API
    participant Provider as Fake Provider
    participant Inbox as Webhook Inbox
    participant Payments as PaymentIntentService
    participant Ledger as LedgerService

    Customer->>API: Create payment intent
    API->>Payments: Authorize payment
    Payments-->>Ledger: No posting; create active hold
    API->>Provider: Create deterministic provider transaction
    Provider-->>API: providerTransactionId

    Provider->>API: POST webhook + HMAC signature
    API->>API: Validate HMAC over raw body
    API->>Inbox: Find provider event ID

    alt Duplicate event
        Inbox-->>API: Existing result
        API-->>Provider: 200 existing status
    else Unknown provider transaction
        API->>Inbox: Store IGNORED event without payment_intent_id
        API-->>Provider: 200 ignored
    else PAYMENT_FAILED
        API->>Provider: Mark transaction FAILED
        API->>Inbox: Store PROCESSED failure event
        API-->>Provider: 200 processed
    else PAYMENT_SUCCEEDED
        API->>Provider: Mark transaction SUCCEEDED
        API->>Payments: Capture with provider idempotency key
        Payments->>Ledger: Balanced capture journal
        API->>Inbox: Store PROCESSED success event
        API-->>Provider: 200 processed
    end
```

Security notes:

- The endpoint is public to JWT authentication but private to the HMAC shared
  secret.
- Signature verification happens before JSON parsing and before inbox writes.
- The inbox persists hashes and safe metadata, not the raw webhook body or raw
  signature.
- Provider event ID uniqueness makes provider retries safe.
- Unknown transaction callbacks are ignored instead of trusting a claimed
  payment intent ID from the provider payload.
