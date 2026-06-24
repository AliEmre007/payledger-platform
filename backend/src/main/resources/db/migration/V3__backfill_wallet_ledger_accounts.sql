INSERT INTO ledger_accounts (
    id,
    account_code,
    account_type,
    normal_balance,
    owner_type,
    wallet_id,
    currency,
    status,
    created_at
)
SELECT
    gen_random_uuid(),
    'WALLET_' || upper(replace(w.id::text, '-', '_')),
    'LIABILITY',
    'CREDIT',
    'CUSTOMER_WALLET',
    w.id,
    w.currency,
    'ACTIVE',
    now()
FROM wallets w
WHERE NOT EXISTS (
    SELECT 1
    FROM ledger_accounts la
    WHERE la.wallet_id = w.id
);
