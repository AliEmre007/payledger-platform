# Merchant Accounting

## Merchant Payable Account Setup

SPRINT-11 introduces merchant onboarding and payable-account provisioning. It
does not authorize, capture, refund, or settle payments yet.

```text
Operations user
  |
  | onboard merchant with settlement currency
  v
Merchant PENDING
  |
  | activate merchant
  v
Merchant ACTIVE
  |
  +--> ledger_accounts owner_type = MERCHANT_PAYABLE
       account_type = LIABILITY
       normal_balance = CREDIT
       merchant_id = merchant
       currency = settlement currency
```

## Future Capture Flow

Payment capture will credit the merchant payable account. Customer wallet
liability will be debited in the same balanced journal entry.

```text
Payment capture, future sprint

Debit   CUSTOMER_WALLET liability
Credit  MERCHANT_PAYABLE liability
```

## Future Settlement Flow

Settlement will debit merchant payable and credit the documented settlement
clearing account. That accounting is intentionally deferred until the
settlement sprint.

```text
Merchant settlement, future sprint

Debit   MERCHANT_PAYABLE liability
Credit  settlement clearing account
```
