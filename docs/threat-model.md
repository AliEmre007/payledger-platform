# PayLedger Initial Threat Model

## Assets to Protect

- Customer identity data.
- Wallet balances and ledger records.
- Payment and transfer commands.
- Administrative actions.
- API credentials and signing secrets.
- Audit records.
- Database backups and logs.

## Trust Boundaries

1. Browser or frontend client to API.
2. API to PostgreSQL.
3. API to Redis.
4. API to external payment-provider simulation.
5. Internal operations user to admin endpoints.
6. CI/CD pipeline to deployment environment.

## Primary Threats

### Broken Authorization
A customer attempts to access another customer's wallet, payment, or transaction by changing an identifier.

Mitigation:
- Object-level authorization checks.
- Role-based access control.
- Integration tests for unauthorized access.

### Duplicate Financial Requests
A network retry creates the same transfer or payment multiple times.

Mitigation:
- Mandatory idempotency keys.
- Database uniqueness constraints.
- Stored response replay for duplicate requests.

### Tampered Webhooks
An attacker sends a fake provider webhook claiming that a payment succeeded.

Mitigation:
- Signature verification.
- Timestamp validation.
- Replay protection.
- Event deduplication.

### Unauthorized Administrative Actions
A low-privilege user freezes an account, changes limits, or views sensitive information.

Mitigation:
- Separate internal roles.
- Least-privilege permissions.
- Immutable audit records.
- Strong authentication for admin users.
- Operations-only endpoints require `ROLE_OPERATIONS`.
- KYC decisions and wallet lifecycle mutations require a reason and write both
  audit events and dedicated history rows.

### Unverified Customer Money Movement
An unverified, rejected, or expired customer attempts to initiate or receive a
wallet transfer.

Mitigation:
- Customer-initiated transfers require `APPROVED` KYC for both source and
  destination customers.
- KYC transitions are explicit and reject skipped states.
- Operations review decisions are auditable and do not store real KYC
  documents, biometric data, OCR output, or third-party provider payloads.

### Frozen or Closed Wallet Movement
A wallet that is frozen for investigation or closed for lifecycle reasons is
used as a transfer source or destination.

Mitigation:
- Transfers require both wallets to be `ACTIVE`.
- Wallet freeze, unfreeze, and close operations are operations-only and audited.
- Wallet close is rejected while the ledger-derived balance is non-zero.

### Sensitive Data Leakage
Secrets, personal data, tokens, or payment information appear in logs or Git history.

Mitigation:
- Secret management.
- Sanitized structured logging.
- .gitignore rules.
- No real card data.
- Code review and CI checks.

### Database Corruption or Loss
Financial records are lost or altered through faulty migration, operator error, or infrastructure failure.

Mitigation:
- Flyway migrations.
- Database constraints.
- Backup and restore testing.
- Immutable financial history.
- Reconciliation jobs.
