CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL
        REFERENCES customers(id)
        ON DELETE RESTRICT,
    source_wallet_id UUID NOT NULL
        REFERENCES wallets(id)
        ON DELETE RESTRICT,
    merchant_id UUID NOT NULL
        REFERENCES merchants(id)
        ON DELETE RESTRICT,
    funds_hold_id UUID NULL
        REFERENCES funds_holds(id)
        ON DELETE RESTRICT,
    amount_minor BIGINT NOT NULL
        CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL
        CHECK (status IN (
            'CREATED',
            'AUTHORIZED',
            'CAPTURED',
            'CANCELED',
            'EXPIRED',
            'FAILED'
        )),
    idempotency_key VARCHAR(255) NOT NULL
        CHECK (btrim(idempotency_key) <> ''),
    request_fingerprint VARCHAR(64) NOT NULL
        CHECK (request_fingerprint ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    authorized_at TIMESTAMPTZ NULL,
    canceled_at TIMESTAMPTZ NULL,
    captured_at TIMESTAMPTZ NULL,
    expired_at TIMESTAMPTZ NULL,
    failed_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),

    CONSTRAINT ck_payment_intents_status_timestamps
        CHECK (
            (status = 'CREATED'
                AND funds_hold_id IS NULL
                AND authorized_at IS NULL
                AND canceled_at IS NULL
                AND captured_at IS NULL
                AND expired_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'AUTHORIZED'
                AND funds_hold_id IS NOT NULL
                AND authorized_at IS NOT NULL
                AND canceled_at IS NULL
                AND captured_at IS NULL
                AND expired_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'CAPTURED'
                AND funds_hold_id IS NOT NULL
                AND authorized_at IS NOT NULL
                AND captured_at IS NOT NULL
                AND canceled_at IS NULL
                AND expired_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'CANCELED'
                AND funds_hold_id IS NOT NULL
                AND authorized_at IS NOT NULL
                AND canceled_at IS NOT NULL
                AND captured_at IS NULL
                AND expired_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'EXPIRED'
                AND expired_at IS NOT NULL
                AND canceled_at IS NULL
                AND captured_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'FAILED'
                AND failed_at IS NOT NULL
                AND canceled_at IS NULL
                AND captured_at IS NULL
                AND expired_at IS NULL)
        )
);

CREATE UNIQUE INDEX ux_payment_intents_customer_idempotency_key
    ON payment_intents (customer_id, idempotency_key);

CREATE UNIQUE INDEX ux_payment_intents_funds_hold_id
    ON payment_intents (funds_hold_id)
    WHERE funds_hold_id IS NOT NULL;

CREATE INDEX idx_payment_intents_customer_created_at
    ON payment_intents (customer_id, created_at);

CREATE INDEX idx_payment_intents_merchant_status
    ON payment_intents (merchant_id, status);

CREATE INDEX idx_payment_intents_wallet_status
    ON payment_intents (source_wallet_id, status);
