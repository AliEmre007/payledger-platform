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
