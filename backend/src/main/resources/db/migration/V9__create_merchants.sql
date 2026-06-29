CREATE TABLE merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name VARCHAR(255) NOT NULL
        CHECK (length(trim(legal_name)) > 0),
    display_name VARCHAR(120) NOT NULL
        CHECK (length(trim(display_name)) > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_merchants_status
    ON merchants (status);

CREATE UNIQUE INDEX ux_merchants_display_name_lower
    ON merchants (lower(display_name));

CREATE TRIGGER trg_merchants_set_updated_at
BEFORE UPDATE ON merchants
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE merchant_settlement_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL
        REFERENCES merchants(id)
        ON DELETE RESTRICT,
    currency VARCHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    settlement_delay_days INTEGER NOT NULL DEFAULT 1
        CHECK (settlement_delay_days BETWEEN 0 AND 30),
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_merchant_settlement_configs_merchant_currency
        UNIQUE (merchant_id, currency)
);

CREATE INDEX idx_merchant_settlement_configs_merchant
    ON merchant_settlement_configs (merchant_id);

CREATE TRIGGER trg_merchant_settlement_configs_set_updated_at
BEFORE UPDATE ON merchant_settlement_configs
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE merchant_lifecycle_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL
        REFERENCES merchants(id)
        ON DELETE RESTRICT,
    from_status VARCHAR(20) NOT NULL
        CHECK (from_status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    to_status VARCHAR(20) NOT NULL
        CHECK (to_status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    reason VARCHAR(500) NOT NULL
        CHECK (length(trim(reason)) > 0),
    actor_external_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_merchant_lifecycle_events_merchant_created_at
    ON merchant_lifecycle_events (merchant_id, created_at);

ALTER TABLE ledger_accounts
    ADD COLUMN merchant_id UUID NULL
        REFERENCES merchants(id)
        ON DELETE RESTRICT;

ALTER TABLE ledger_accounts
    DROP CONSTRAINT ck_ledger_accounts_wallet_ownership;

ALTER TABLE ledger_accounts
    ADD CONSTRAINT ck_ledger_accounts_owner_reference
        CHECK (
            (
                owner_type = 'CUSTOMER_WALLET'
                AND wallet_id IS NOT NULL
                AND merchant_id IS NULL
                AND account_type = 'LIABILITY'
                AND normal_balance = 'CREDIT'
            )
            OR
            (
                owner_type = 'MERCHANT_PAYABLE'
                AND wallet_id IS NULL
                AND merchant_id IS NOT NULL
                AND account_type = 'LIABILITY'
                AND normal_balance = 'CREDIT'
            )
            OR
            (
                owner_type NOT IN ('CUSTOMER_WALLET', 'MERCHANT_PAYABLE')
                AND wallet_id IS NULL
                AND merchant_id IS NULL
            )
        );

CREATE UNIQUE INDEX ux_ledger_accounts_merchant_currency
    ON ledger_accounts (merchant_id, currency)
    WHERE merchant_id IS NOT NULL
      AND owner_type = 'MERCHANT_PAYABLE';

CREATE INDEX idx_ledger_accounts_merchant_id
    ON ledger_accounts (merchant_id)
    WHERE merchant_id IS NOT NULL;
