CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    source_wallet_id UUID NOT NULL
        REFERENCES wallets(id)
        ON DELETE RESTRICT,
    destination_wallet_id UUID NOT NULL
        REFERENCES wallets(id)
        ON DELETE RESTRICT,
    initiated_by_customer_id UUID NOT NULL
        REFERENCES customers(id)
        ON DELETE RESTRICT,
    journal_entry_id UUID NOT NULL UNIQUE
        REFERENCES journal_entries(id)
        ON DELETE RESTRICT,
    amount_minor BIGINT NOT NULL
        CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('COMPLETED')),
    idempotency_key VARCHAR(255) NOT NULL
        CHECK (btrim(idempotency_key) <> ''),
    request_fingerprint VARCHAR(64) NOT NULL
        CHECK (request_fingerprint ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_transfers_distinct_wallets
        CHECK (source_wallet_id <> destination_wallet_id)
);

CREATE UNIQUE INDEX ux_transfers_source_wallet_idempotency_key
    ON transfers (source_wallet_id, idempotency_key);

CREATE INDEX idx_transfers_source_wallet_created_at
    ON transfers (source_wallet_id, created_at DESC);

CREATE INDEX idx_transfers_destination_wallet_created_at
    ON transfers (destination_wallet_id, created_at DESC);

CREATE OR REPLACE FUNCTION validate_transfer_integrity()
RETURNS TRIGGER AS $$
DECLARE
    source_customer_id UUID;
    source_currency VARCHAR(3);
    destination_currency VARCHAR(3);
    journal_currency VARCHAR(3);
    journal_reference_type VARCHAR(50);
    journal_reference_id UUID;
BEGIN
    SELECT customer_id, currency
    INTO source_customer_id, source_currency
    FROM wallets
    WHERE id = NEW.source_wallet_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Source wallet % does not exist', NEW.source_wallet_id;
    END IF;

    SELECT currency
    INTO destination_currency
    FROM wallets
    WHERE id = NEW.destination_wallet_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Destination wallet % does not exist', NEW.destination_wallet_id;
    END IF;

    IF NEW.initiated_by_customer_id <> source_customer_id THEN
        RAISE EXCEPTION
            'Transfer initiator must own the source wallet';
    END IF;

    IF NEW.currency <> source_currency
       OR NEW.currency <> destination_currency THEN
        RAISE EXCEPTION
            'Transfer currency % must match both wallet currencies',
            NEW.currency;
    END IF;

    SELECT currency, reference_type, reference_id
    INTO journal_currency, journal_reference_type, journal_reference_id
    FROM journal_entries
    WHERE id = NEW.journal_entry_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'Journal entry % does not exist',
            NEW.journal_entry_id;
    END IF;

    IF journal_currency <> NEW.currency THEN
        RAISE EXCEPTION
            'Transfer currency % must match journal currency %',
            NEW.currency,
            journal_currency;
    END IF;

    IF journal_reference_type <> 'TRANSFER'
       OR journal_reference_id <> NEW.id THEN
        RAISE EXCEPTION
            'Journal entry must reference this transfer';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_transfer_integrity
BEFORE INSERT OR UPDATE ON transfers
FOR EACH ROW
EXECUTE FUNCTION validate_transfer_integrity();
