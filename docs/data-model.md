# PayLedger Data Model

PayLedger uses PostgreSQL as the transactional source of truth. Hibernate
validates mappings, but Flyway owns schema changes.

## Core Entities

```text
customers
  -> customer_identities
  -> wallets
       -> ledger_accounts
       -> funds_holds

journal_entries
  -> ledger_postings

transfers
payment_intents
merchants
settlement_batches
  -> settlement_lines
reconciliation_cases

audit_events
outbox_events
notifications
provider_transactions
provider_webhook_inbox
```

## Financial Tables

`journal_entries` identifies the financial event and reference record.
`ledger_postings` stores immutable debit and credit postings. Database
constraints and triggers enforce positive postings, matching currency, reversal
rules, append-only history, and balanced journals at commit.

Wallet balances are calculated from ledger postings. There is no mutable wallet
balance column.

## Available Balance

Funds holds reserve available balance without creating ledger postings.

```text
ledger balance - active holds = available balance
```

Capture moves the hold to a consumed state and creates the payment capture
journal. Release removes the reservation without ledger movement.

## Idempotency

Retriable commands store idempotency keys and request fingerprints on their
business records. The same key and same request replays the original result.
The same key with a different request is rejected.

## Audit And Outbox

`audit_events` and `outbox_events` are append-only. Business services write
them in the same transaction as the business record and financial journal when
the workflow requires auditability or asynchronous side effects.

No audit metadata or outbox payload should contain bearer tokens, passwords,
secrets, real KYC documents, or unnecessary PII.
