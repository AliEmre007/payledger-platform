CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- Customer domain
-- ---------------------------------------------------------------------------

CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_type VARCHAR(20) NOT NULL
        CHECK (customer_type IN ('PERSONAL', 'BUSINESS')),
    legal_name VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_KYC'
        CHECK (status IN ('PENDING_KYC', 'ACTIVE', 'RESTRICTED', 'SUSPENDED', 'CLOSED')),
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED'
        CHECK (kyc_status IN ('NOT_STARTED', 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_customers_email_lower
    ON customers (lower(email));

-- ---------------------------------------------------------------------------
-- Wallet domain
-- A wallet is customer-facing. It does not own mutable money balance fields.
-- ---------------------------------------------------------------------------

CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL
        REFERENCES customers(id)
        ON DELETE RESTRICT,
    currency CHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_wallets_customer_currency UNIQUE (customer_id, currency)
);

CREATE INDEX idx_wallets_customer_id
    ON wallets (customer_id);

-- ---------------------------------------------------------------------------
-- Ledger domain
-- The ledger is the financial source of truth.
-- ---------------------------------------------------------------------------

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_code VARCHAR(120) NOT NULL UNIQUE
        CHECK (account_code = upper(account_code)),
    account_type VARCHAR(20) NOT NULL
        CHECK (account_type IN ('ASSET', 'LIABILITY', 'REVENUE', 'EXPENSE', 'EQUITY')),
    normal_balance VARCHAR(10) NOT NULL
        CHECK (normal_balance IN ('DEBIT', 'CREDIT')),
    owner_type VARCHAR(30) NOT NULL
        CHECK (owner_type IN (
            'CUSTOMER_WALLET',
            'MERCHANT_PAYABLE',
            'PLATFORM',
            'EXTERNAL_CLEARING'
        )),
    wallet_id UUID NULL
        REFERENCES wallets(id)
        ON DELETE RESTRICT,
    currency CHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_ledger_accounts_normal_balance
        CHECK (
            (account_type IN ('ASSET', 'EXPENSE') AND normal_balance = 'DEBIT')
            OR
            (account_type IN ('LIABILITY', 'REVENUE', 'EQUITY') AND normal_balance = 'CREDIT')
        ),

    CONSTRAINT ck_ledger_accounts_wallet_ownership
        CHECK (
            (
                owner_type = 'CUSTOMER_WALLET'
                AND wallet_id IS NOT NULL
                AND account_type = 'LIABILITY'
                AND normal_balance = 'CREDIT'
            )
            OR
            (
                owner_type <> 'CUSTOMER_WALLET'
                AND wallet_id IS NULL
            )
        )
);

CREATE UNIQUE INDEX ux_ledger_accounts_wallet_id
    ON ledger_accounts (wallet_id)
    WHERE wallet_id IS NOT NULL;

-- Journal entries are immutable financial events.
-- One entry supports one currency in Sprint 1.
CREATE TABLE journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_kind VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
        CHECK (entry_kind IN ('NORMAL', 'REVERSAL')),
    journal_type VARCHAR(50) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id UUID NOT NULL,
    currency CHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    reverses_journal_entry_id UUID NULL UNIQUE
        REFERENCES journal_entries(id)
        ON DELETE RESTRICT,
    description VARCHAR(500) NULL,
    effective_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_journal_entries_reversal_reference
        CHECK (
            (entry_kind = 'NORMAL' AND reverses_journal_entry_id IS NULL)
            OR
            (entry_kind = 'REVERSAL' AND reverses_journal_entry_id IS NOT NULL)
        )
);

CREATE INDEX idx_journal_entries_reference
    ON journal_entries (reference_type, reference_id);

CREATE INDEX idx_journal_entries_effective_at
    ON journal_entries (effective_at);

-- A posting is one debit or credit line inside a journal entry.
CREATE TABLE ledger_postings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL
        REFERENCES journal_entries(id)
        ON DELETE RESTRICT,
    ledger_account_id UUID NOT NULL
        REFERENCES ledger_accounts(id)
        ON DELETE RESTRICT,
    line_number SMALLINT NOT NULL
        CHECK (line_number > 0),
    direction VARCHAR(10) NOT NULL
        CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT NOT NULL
        CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_ledger_postings_entry_line
        UNIQUE (journal_entry_id, line_number)
);

CREATE INDEX idx_ledger_postings_journal_entry_id
    ON ledger_postings (journal_entry_id);

CREATE INDEX idx_ledger_postings_ledger_account_id
    ON ledger_postings (ledger_account_id);

-- ---------------------------------------------------------------------------
-- Operational triggers
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customers_set_updated_at
BEFORE UPDATE ON customers
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_wallets_set_updated_at
BEFORE UPDATE ON wallets
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- A customer wallet's ledger account must use the same currency as its wallet.
CREATE OR REPLACE FUNCTION validate_wallet_ledger_account_currency()
RETURNS TRIGGER AS $$
DECLARE
    wallet_currency CHAR(3);
BEGIN
    IF NEW.wallet_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT currency
    INTO wallet_currency
    FROM wallets
    WHERE id = NEW.wallet_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Wallet % does not exist', NEW.wallet_id;
    END IF;

    IF NEW.currency <> wallet_currency THEN
        RAISE EXCEPTION
            'Ledger account currency % must match wallet currency %',
            NEW.currency,
            wallet_currency;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_wallet_ledger_account_currency
BEFORE INSERT OR UPDATE ON ledger_accounts
FOR EACH ROW
EXECUTE FUNCTION validate_wallet_ledger_account_currency();

-- A reversal may only reverse a normal entry in the same currency.
CREATE OR REPLACE FUNCTION validate_reversal_journal_entry()
RETURNS TRIGGER AS $$
DECLARE
    original_currency CHAR(3);
    original_kind VARCHAR(20);
BEGIN
    IF NEW.entry_kind <> 'REVERSAL' THEN
        RETURN NEW;
    END IF;

    SELECT currency, entry_kind
    INTO original_currency, original_kind
    FROM journal_entries
    WHERE id = NEW.reverses_journal_entry_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Journal entry % cannot be reversed because it does not exist',
            NEW.reverses_journal_entry_id;
    END IF;

    IF original_kind <> 'NORMAL' THEN
        RAISE EXCEPTION
            'Only a normal journal entry may be reversed';
    END IF;

    IF NEW.currency <> original_currency THEN
        RAISE EXCEPTION
            'Reversal currency % must match original currency %',
            NEW.currency,
            original_currency;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_reversal_journal_entry
BEFORE INSERT ON journal_entries
FOR EACH ROW
EXECUTE FUNCTION validate_reversal_journal_entry();

-- Every posting must use the same currency as both its journal entry and account.
CREATE OR REPLACE FUNCTION validate_ledger_posting_currency()
RETURNS TRIGGER AS $$
DECLARE
    account_currency CHAR(3);
    entry_currency CHAR(3);
BEGIN
    SELECT currency
    INTO account_currency
    FROM ledger_accounts
    WHERE id = NEW.ledger_account_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Ledger account % does not exist', NEW.ledger_account_id;
    END IF;

    SELECT currency
    INTO entry_currency
    FROM journal_entries
    WHERE id = NEW.journal_entry_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Journal entry % does not exist', NEW.journal_entry_id;
    END IF;

    IF NEW.currency <> account_currency THEN
        RAISE EXCEPTION
            'Posting currency % must match account currency %',
            NEW.currency,
            account_currency;
    END IF;

    IF NEW.currency <> entry_currency THEN
        RAISE EXCEPTION
            'Posting currency % must match journal currency %',
            NEW.currency,
            entry_currency;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_ledger_posting_currency
BEFORE INSERT OR UPDATE ON ledger_postings
FOR EACH ROW
EXECUTE FUNCTION validate_ledger_posting_currency();

-- Journal entries and postings are append-only.
-- Corrections must be represented with a reversing journal entry.
CREATE OR REPLACE FUNCTION prevent_financial_record_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Financial records are immutable. Create a reversal instead of updating or deleting them.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_entries_immutable
BEFORE UPDATE OR DELETE ON journal_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_financial_record_mutation();

CREATE TRIGGER trg_ledger_postings_immutable
BEFORE UPDATE OR DELETE ON ledger_postings
FOR EACH ROW
EXECUTE FUNCTION prevent_financial_record_mutation();

-- At transaction commit, every journal entry must have:
-- 1. at least two postings
-- 2. equal total debits and credits
CREATE OR REPLACE FUNCTION validate_journal_entry_balance()
RETURNS TRIGGER AS $$
DECLARE
    target_entry_id UUID;
    debit_total BIGINT;
    credit_total BIGINT;
    posting_count INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'journal_entries' THEN
        IF TG_OP = 'DELETE' THEN
            target_entry_id := OLD.id;
        ELSE
            target_entry_id := NEW.id;
        END IF;
    ELSE
        IF TG_OP = 'DELETE' THEN
            target_entry_id := OLD.journal_entry_id;
        ELSE
            target_entry_id := NEW.journal_entry_id;
        END IF;
    END IF;

    SELECT
        COUNT(*),
        COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_minor ELSE 0 END), 0)
    INTO posting_count, debit_total, credit_total
    FROM ledger_postings
    WHERE journal_entry_id = target_entry_id;

    IF posting_count < 2 THEN
        RAISE EXCEPTION
            'Journal entry % must contain at least two ledger postings',
            target_entry_id;
    END IF;

    IF debit_total <> credit_total THEN
        RAISE EXCEPTION
            'Journal entry % is not balanced: debits %, credits %',
            target_entry_id,
            debit_total,
            credit_total;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_journal_entries_must_balance
AFTER INSERT OR UPDATE OR DELETE ON journal_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_journal_entry_balance();

CREATE CONSTRAINT TRIGGER trg_ledger_postings_must_balance
AFTER INSERT OR UPDATE OR DELETE ON ledger_postings
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_journal_entry_balance();
