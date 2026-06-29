# Payment Intents

## State Machine

SPRINT-13 implements capture and refund on top of authorization and customer
cancellation.

```text
CREATED -> AUTHORIZED
AUTHORIZED -> CANCELED
AUTHORIZED -> CAPTURED
CAPTURED -> REFUNDED

Future sprints:
AUTHORIZED -> EXPIRED
AUTHORIZED -> FAILED
```

`CREATED` is an internal transient state used while building an authorization.
The public authorization API returns `AUTHORIZED` after the funds hold has been
created. Failed authorization does not persist a payment intent.

`AUTHORIZED` means customer funds are reserved by an `ACTIVE` funds hold. It is
not a ledger posting and does not move money.

`CAPTURED` consumes the active hold and posts the payment ledger journal.

`REFUNDED` posts a separate compensating journal. It never edits or deletes the
original capture journal.

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

## Capture API

```text
POST /api/v1/operations/payment-intents/{paymentIntentId}/capture
Idempotency-Key: <operator-generated key>
Authorization: Bearer <operations JWT>
```

Rules:

- Only operations users can capture until a merchant-auth surface exists.
- Only `AUTHORIZED` payment intents can be captured.
- Capture consumes the active funds hold.
- Capture emits audit and outbox records.
- Repeating capture with the same idempotency key returns the captured payment
  intent without duplicate side effects.

Capture creates one balanced journal:

```text
Debit   CUSTOMER_WALLET liability
Credit  MERCHANT_PAYABLE liability
```

Example, for a 25.00 TRY capture:

```text
Debit   customer wallet liability     2500 TRY
Credit  merchant payable liability    2500 TRY
```

This lowers the customer's ledger-derived wallet balance and increases the
merchant payable balance by the same amount.

## Refund API

```text
POST /api/v1/operations/payment-intents/{paymentIntentId}/refund
Idempotency-Key: <operator-generated key>
Authorization: Bearer <operations JWT>
```

Rules:

- Only operations users can refund until a merchant-auth surface exists.
- Only `CAPTURED` payment intents can be refunded.
- Refund emits audit and outbox records.
- Repeating refund with the same idempotency key returns the refunded payment
  intent without duplicate side effects.

Refund creates a new balanced journal. It does not modify the capture journal:

```text
Debit   MERCHANT_PAYABLE liability
Credit  CUSTOMER_WALLET liability
```

Example, for a 25.00 TRY refund:

```text
Debit   merchant payable liability    2500 TRY
Credit  customer wallet liability     2500 TRY
```

This restores the customer's ledger-derived wallet balance and reduces the
merchant payable balance by the same amount.
