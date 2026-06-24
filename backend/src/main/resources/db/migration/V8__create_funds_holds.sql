CREATE TABLE funds_holds (
    id UUID PRIMARY KEY,
    wallet_id UUID NOT NULL
        REFERENCES wallets(id)
        ON DELETE RESTRICT,
    currency VARCHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    amount_minor BIGINT NOT NULL
        CHECK (amount_minor > 0),
    reason VARCHAR(500) NOT NULL
        CHECK (btrim(reason) <> ''),
    reference_type VARCHAR(50) NOT NULL
        CHECK (btrim(reference_type) <> ''),
    reference_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED', 'EXPIRED')),
    idempotency_key VARCHAR(255) NOT NULL
        CHECK (btrim(idempotency_key) <> ''),
    request_fingerprint VARCHAR(64) NOT NULL
        CHECK (request_fingerprint ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    captured_at TIMESTAMPTZ NULL,
    released_at TIMESTAMPTZ NULL,
    expired_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0
        CHECK (version >= 0),

    CONSTRAINT ck_funds_holds_terminal_timestamp
        CHECK (
            (status = 'ACTIVE'
                AND captured_at IS NULL
                AND released_at IS NULL
                AND expired_at IS NULL)
            OR
            (status = 'CAPTURED'
                AND captured_at IS NOT NULL
                AND released_at IS NULL
                AND expired_at IS NULL)
            OR
            (status = 'RELEASED'
                AND captured_at IS NULL
                AND released_at IS NOT NULL
                AND expired_at IS NULL)
            OR
            (status = 'EXPIRED'
                AND captured_at IS NULL
                AND released_at IS NULL
                AND expired_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX ux_funds_holds_wallet_idempotency_key
    ON funds_holds (wallet_id, idempotency_key);

CREATE UNIQUE INDEX ux_funds_holds_wallet_reference
    ON funds_holds (wallet_id, reference_type, reference_id);

CREATE INDEX idx_funds_holds_wallet_status
    ON funds_holds (wallet_id, status);

CREATE INDEX idx_funds_holds_reference
    ON funds_holds (reference_type, reference_id);

CREATE OR REPLACE FUNCTION validate_funds_hold_wallet()
RETURNS TRIGGER AS $$
DECLARE
    wallet_currency VARCHAR(3);
BEGIN
    SELECT currency
    INTO wallet_currency
    FROM wallets
    WHERE id = NEW.wallet_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Wallet % does not exist', NEW.wallet_id;
    END IF;

    IF NEW.currency <> wallet_currency THEN
        RAISE EXCEPTION
            'Funds hold currency % must match wallet currency %',
            NEW.currency,
            wallet_currency;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_funds_hold_wallet
BEFORE INSERT OR UPDATE ON funds_holds
FOR EACH ROW
EXECUTE FUNCTION validate_funds_hold_wallet();
