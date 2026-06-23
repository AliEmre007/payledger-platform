ALTER TABLE wallets
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING currency::VARCHAR(3);

ALTER TABLE ledger_accounts
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING currency::VARCHAR(3);

ALTER TABLE journal_entries
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING currency::VARCHAR(3);

ALTER TABLE ledger_postings
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING currency::VARCHAR(3);
