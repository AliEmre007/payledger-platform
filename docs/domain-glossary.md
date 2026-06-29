# PayLedger Domain Glossary

## Customer
An end user who owns one or more wallets and can initiate approved financial operations.

Customers carry a KYC status. Stored values are:

- `NOT_STARTED`: no review has been submitted.
- `PENDING`: pending operations review.
- `APPROVED`: verified; eligible for customer-initiated money movement.
- `REJECTED`: review failed and must be explicitly resubmitted.
- `EXPIRED`: verification is no longer current.

The product language may call `PENDING` "pending review" and `APPROVED`
"verified"; the database keeps the existing enum names for compatibility.

## Merchant
A business entity that accepts payments and receives settlement.

Merchant lifecycle states are:

- `PENDING`: onboarded by operations, but not yet eligible to receive payment
  intents.
- `ACTIVE`: eligible to receive payment intents in enabled settlement
  currencies.
- `SUSPENDED`: temporarily blocked from new payment intents.
- `CLOSED`: terminal state for a merchant relationship.

Each enabled merchant settlement currency has one `MERCHANT_PAYABLE` liability
ledger account once the merchant is active. Payment capture will credit this
account in a later sprint; settlement will debit it when merchant funds are
moved into the settlement workflow.

## Wallet
A customer-facing financial container associated with one or more ledger accounts.

Wallet lifecycle states are:

- `ACTIVE`: can participate in permitted money movement.
- `FROZEN`: cannot debit or receive customer-initiated transfers until an
  operations user unfreezes it.
- `CLOSED`: terminal state; allowed only when the ledger-derived balance is zero.

## Ledger Account
An accounting account that records money movement. Examples include customer wallet liability, merchant payable, platform fee revenue, and external clearing accounts.

Merchant payable accounts are owned by a merchant and currency. They are
liability accounts with a credit normal balance because they represent amounts
PayLedger owes to the merchant.

## Journal Entry
A single financial event containing one or more balanced ledger postings.

## Ledger Posting
An immutable debit or credit record inside a journal entry.

## Double-Entry Ledger
An accounting model in which every journal entry has equal total debits and credits.

## Available Balance
The amount a customer may currently spend or transfer.

## Pending Balance
The amount reserved or awaiting final settlement, capture, reversal, or failure.

## Transfer
A movement of funds between internal wallet accounts.

Transfers require the source wallet to belong to the authenticated customer,
both source and destination customers to have `APPROVED` KYC, and both wallets
to be `ACTIVE`.

## Payment Intent
A payment request that moves through explicit lifecycle states before final completion.

## Settlement
The process of making merchant funds available after payment processing and fee calculation.

## Reconciliation
Comparing PayLedger records against an external-provider statement to detect missing, duplicated, or mismatched transactions.

## Refund
Returning funds to the original payer after a completed payment.

## Reversal
A compensating financial event that cancels the economic effect of an earlier event without editing historical records.

## Idempotency Key
A client-provided unique key that ensures retrying the same financial request does not execute it twice.

## Webhook
An asynchronous HTTP callback sent by an external provider to report an event, such as payment success or failure.

## Outbox Event
A database record written within the same transaction as a business change, then published safely to an event system later.

## Audit Event
An immutable record describing who performed a sensitive action, when it occurred, and what changed.

Operations KYC decisions and wallet lifecycle changes also write dedicated
history rows with the previous state, next state, actor subject, and reason.
