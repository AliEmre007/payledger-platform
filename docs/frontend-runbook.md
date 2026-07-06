# PayLedger Frontend Runbook

## Purpose

The frontend is a TypeScript single-page application that exercises the secured
PayLedger API. It does not calculate balances, mutate financial state locally,
or replace backend authorization and business rules.

## Local Prerequisites

- Node.js 22 or newer.
- Backend API running on `http://localhost:18080`.
- Local Keycloak running on `http://localhost:18081`.
- Local realm `payledger-local` imported from
  `infra/compose/keycloak/import/payledger-local-realm.json`.

Start local infrastructure from the repository root:

```bash
docker compose -f infra/compose/compose.yaml up -d
```

Start the backend:

```bash
cd backend
./mvnw spring-boot:run
```

Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open:

```text
http://localhost:5173
```

The Vite development server proxies `/api`, `/actuator`, and `/api-docs` to the
backend, so browser calls use same-origin API paths during local development.

## Authentication

The browser uses Keycloak Authorization Code Flow with PKCE through client:

```text
payledger-frontend
```

The browser client is public, uses `S256` PKCE, and has direct password grants
disabled. The existing `payledger-dev-cli` client remains development-only for
terminal workflows and is not used by browser code.

Default local user:

```text
username: alice
password: alice-dev-password
realm role: CUSTOMER
```

Operations screens are visible only when the access token contains
`OPERATIONS` or `ADMIN` in `realm_access.roles`.

## Customer Workflows

The customer workspace supports:

- login and logout;
- wallet balance lookup by wallet ID;
- paginated statement lookup for the selected wallet;
- wallet-to-wallet transfer with a stable idempotency key for the current
  transfer draft;
- payment authorization by wallet and merchant ID;
- cancellation of the most recently authorized payment shown in the UI.

The backend remains the source of truth. Balances displayed after commands are
fetched from API responses and follow ledger/hold semantics.

## Operations Workflows

The operations workspace supports:

- customer KYC submit/approve/reject;
- wallet freeze/unfreeze/close;
- merchant onboarding and activation;
- payment capture/refund;
- settlement batch creation;
- settlement reconciliation;
- paginated reads for audit events, customers, wallets, payment intents, and
  reconciliation cases.

All operations mutations require a reason and rely on backend role checks. The
frontend hides operations screens for customer-only tokens, but the API remains
the enforcement point.

## Configuration

Optional Vite environment variables:

```text
VITE_API_BASE_URL=
VITE_KEYCLOAK_URL=http://localhost:18081
VITE_KEYCLOAK_REALM=payledger-local
VITE_KEYCLOAK_CLIENT_ID=payledger-frontend
```

For local development, `VITE_API_BASE_URL` should usually stay empty so the
Vite proxy handles backend calls.

## Verification

Run frontend checks:

```bash
cd frontend
npm run build
npm test
```

Run backend checks before completing the sprint:

```bash
cd backend
./mvnw test
```

Then from the repository root:

```bash
git diff --check
git status --short
```
