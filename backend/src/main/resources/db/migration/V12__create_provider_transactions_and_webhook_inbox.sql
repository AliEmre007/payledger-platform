CREATE TABLE provider_transactions (
    id UUID PRIMARY KEY,
    provider_name VARCHAR(50) NOT NULL
        CHECK (provider_name = upper(provider_name)),
    provider_transaction_id VARCHAR(120) NOT NULL
        CHECK (btrim(provider_transaction_id) <> ''),
    payment_intent_id UUID NOT NULL
        REFERENCES payment_intents(id)
        ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    requested_outcome VARCHAR(20) NOT NULL
        CHECK (requested_outcome IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_provider_transactions_provider_id
        UNIQUE (provider_name, provider_transaction_id),
    CONSTRAINT uq_provider_transactions_payment_intent
        UNIQUE (payment_intent_id)
);

CREATE INDEX idx_provider_transactions_payment_intent
    ON provider_transactions (payment_intent_id);

CREATE INDEX idx_provider_transactions_status_created_at
    ON provider_transactions (status, created_at);

CREATE TRIGGER trg_provider_transactions_set_updated_at
BEFORE UPDATE ON provider_transactions
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE provider_webhook_events (
    id UUID PRIMARY KEY,
    provider_name VARCHAR(50) NOT NULL
        CHECK (provider_name = upper(provider_name)),
    provider_event_id VARCHAR(120) NOT NULL
        CHECK (btrim(provider_event_id) <> ''),
    provider_transaction_id VARCHAR(120) NOT NULL
        CHECK (btrim(provider_transaction_id) <> ''),
    payment_intent_id UUID NULL
        REFERENCES payment_intents(id)
        ON DELETE RESTRICT,
    event_type VARCHAR(50) NOT NULL
        CHECK (event_type IN ('PAYMENT_SUCCEEDED', 'PAYMENT_FAILED')),
    payload_sha256 VARCHAR(64) NOT NULL
        CHECK (payload_sha256 ~ '^[a-f0-9]{64}$'),
    signature_sha256 VARCHAR(64) NOT NULL
        CHECK (signature_sha256 ~ '^[a-f0-9]{64}$'),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PROCESSED', 'IGNORED', 'FAILED')),
    ignored_reason VARCHAR(120) NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(metadata) = 'object'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ NULL,

    CONSTRAINT uq_provider_webhook_events_provider_event
        UNIQUE (provider_name, provider_event_id),
    CONSTRAINT ck_provider_webhook_events_processed_at
        CHECK (
            (status = 'PROCESSED' AND processed_at IS NOT NULL)
            OR
            (status IN ('IGNORED', 'FAILED'))
        )
);

CREATE INDEX idx_provider_webhook_events_transaction
    ON provider_webhook_events (provider_name, provider_transaction_id);

CREATE INDEX idx_provider_webhook_events_payment_intent
    ON provider_webhook_events (payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;

CREATE INDEX idx_provider_webhook_events_received_at
    ON provider_webhook_events (received_at);
