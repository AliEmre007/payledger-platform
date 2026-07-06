# PayLedger Demo Workflow

This workflow demonstrates PayLedger as a simulated fintech platform. Use test
customers, test merchants, and local-only credentials. Do not enter real card
data, real identity documents, production secrets, or real customer data.

## 1. Start The Stack

```bash
docker compose -f infra/compose/compose.yaml up -d
cd backend
./mvnw test
```

For a production-style container check, follow
[deployment-runbook.md](deployment-runbook.md).

## 2. Customer Onboarding And KYC

Create or seed two simulated customers and wallets through the backend services
or the UI. Operations then submits and approves mock KYC:

- submit KYC review for the customer;
- approve KYC with an operations reason;
- confirm customer status is approved;
- confirm wallet status is active.

Expected evidence:

- customer row exists;
- wallet row exists;
- customer-wallet ledger account exists;
- KYC history rows exist;
- audit events exist for KYC decisions.

## 3. Wallet Funding Simulation

Fund the customer wallet through the simulated top-up journal used by tests and
local demos. This is not a real external deposit.

Expected evidence:

- balanced `WALLET_TOP_UP` journal;
- debit posting to platform cash;
- credit posting to the customer wallet ledger account;
- wallet balance reads from ledger postings.

## 4. Customer Transfer

Initiate a same-currency transfer from the authenticated customer wallet to
another approved customer wallet.

Expected evidence:

- `transfers` business record;
- balanced transfer journal;
- debit posting from source wallet;
- credit posting to destination wallet;
- audit event and outbox event for `TRANSFER_COMPLETED`;
- idempotency replay returns the same transfer.

## 5. Payment Authorization, Capture, And Refund

Authorize a payment from an approved customer wallet to an active merchant.
Authorization creates a hold and lowers available balance without creating a
payment ledger journal. Operations capture later creates the financial journal.
Refund creates a separate compensating journal.

Expected evidence:

- `payment_intents` record moves through `AUTHORIZED`, `CAPTURED`, and
  `REFUNDED`;
- `funds_holds` record is active after authorization and captured after capture;
- capture journal debits customer wallet and credits merchant payable;
- refund journal debits merchant payable and credits customer wallet;
- audit and outbox events exist for each payment state change.

## 6. Settlement And Reconciliation

Create another captured merchant payment, then run an operations settlement
batch for the merchant and currency. Reconcile with a mismatched provider
amount to open a discrepancy case.

Expected evidence:

- settlement batch and settlement line records;
- balanced `MERCHANT_SETTLEMENT` journal;
- captured payment claimed by exactly one settlement line;
- reconciliation case with `AMOUNT_MISMATCH`;
- operations audit and outbox events for settlement and reconciliation.

## 7. Operations Review

Use operations read APIs or the UI to review:

- customers by KYC status;
- wallets by lifecycle status;
- payment intents by status;
- reconciliation cases by status;
- audit event history.

Operations screens and APIs require `ROLE_OPERATIONS`; customer JWTs must not
access them.

## Automated Evidence

`PortfolioScenarioIntegrationTest` runs the cohesive scenario in the backend
test suite and verifies that core financial events are traceable through:

```text
business record -> journal entry -> ledger postings -> audit event -> outbox event
```

The full suite is the final verification gate:

```bash
cd backend
./mvnw test
cd ../frontend
npm ci
npm audit --omit=dev
npm run build
npm test
```
