# Payment Intents

## State Machine

SPRINT-12 implements authorization and customer cancellation only.

```text
CREATED -> AUTHORIZED
AUTHORIZED -> CANCELED

Future sprints:
AUTHORIZED -> CAPTURED
AUTHORIZED -> EXPIRED
AUTHORIZED -> FAILED
```

`CREATED` is an internal transient state used while building an authorization.
The public authorization API returns `AUTHORIZED` after the funds hold has been
created. Failed authorization does not persist a payment intent.

`AUTHORIZED` means customer funds are reserved by an `ACTIVE` funds hold. It is
not a ledger posting and does not move money.

`CANCELED` releases the active hold. Repeating cancellation for an already
canceled payment intent returns the canceled intent without emitting duplicate
side effects.

## Authorization API

```text
POST /api/v1/payment-intents
Idempotency-Key: <client-generated key>
Authorization: Bearer <customer JWT>

{
  "sourceWalletId": "uuid",
  "merchantId": "uuid",
  "amountMinor": 2500,
  "currency": "TRY"
}
```

Rules:

- The JWT `sub` must resolve to a linked PayLedger customer.
- The source wallet must belong to the authenticated customer.
- The customer must have approved KYC.
- The source wallet must be `ACTIVE`.
- The merchant must be `ACTIVE` and enabled for the requested currency.
- The payment currency must match the source wallet currency.
- Available balance must cover the requested amount.
- Authorization creates one `ACTIVE` funds hold with reference type
  `PAYMENT_INTENT`.
- Authorization emits audit and outbox records.
- Authorization does not create a journal entry or ledger posting.

Repeated use of the same idempotency key by the same customer returns the same
payment intent when the request fingerprint matches. Reusing the key for a
different request is rejected with an idempotency conflict.

## Cancellation API

```text
POST /api/v1/payment-intents/{paymentIntentId}/cancel
Authorization: Bearer <customer JWT>
```

Rules:

- The JWT `sub` must resolve to the payment intent owner.
- Only `AUTHORIZED` payment intents can transition to `CANCELED`.
- Cancellation releases the active hold.
- Repeating cancellation is safe and returns the existing canceled intent.
- Cancellation emits audit and outbox records only for the first transition.
