CREATE TABLE settlement_batches (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL
        REFERENCES merchants(id)
        ON DELETE RESTRICT,
    currency VARCHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('COMPLETED')),
    total_amount_minor BIGINT NOT NULL
        CHECK (total_amount_minor > 0),
    journal_entry_id UUID NOT NULL
        REFERENCES journal_entries(id)
        ON DELETE RESTRICT,
    idempotency_key VARCHAR(255) NOT NULL
        CHECK (btrim(idempotency_key) <> ''),
    actor_external_subject VARCHAR(255) NOT NULL
        CHECK (btrim(actor_external_subject) <> ''),
    reason VARCHAR(500) NOT NULL
        CHECK (btrim(reason) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_settlement_batches_merchant_idempotency
        UNIQUE (merchant_id, idempotency_key),
    CONSTRAINT uq_settlement_batches_journal
        UNIQUE (journal_entry_id)
);

CREATE INDEX idx_settlement_batches_merchant_created_at
    ON settlement_batches (merchant_id, created_at);

CREATE INDEX idx_settlement_batches_status_created_at
    ON settlement_batches (status, created_at);

CREATE TABLE settlement_lines (
    id UUID PRIMARY KEY,
    settlement_batch_id UUID NOT NULL
        REFERENCES settlement_batches(id)
        ON DELETE RESTRICT,
    payment_intent_id UUID NOT NULL
        REFERENCES payment_intents(id)
        ON DELETE RESTRICT,
    capture_journal_entry_id UUID NOT NULL
        REFERENCES journal_entries(id)
        ON DELETE RESTRICT,
    amount_minor BIGINT NOT NULL
        CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL
        CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_settlement_lines_payment_intent
        UNIQUE (payment_intent_id),
    CONSTRAINT uq_settlement_lines_capture_journal
        UNIQUE (capture_journal_entry_id)
);

CREATE INDEX idx_settlement_lines_batch
    ON settlement_lines (settlement_batch_id);

CREATE TABLE reconciliation_cases (
    id UUID PRIMARY KEY,
    settlement_batch_id UUID NOT NULL
        REFERENCES settlement_batches(id)
        ON DELETE RESTRICT,
    merchant_id UUID NOT NULL
        REFERENCES merchants(id)
        ON DELETE RESTRICT,
    provider_reference VARCHAR(120) NOT NULL
        CHECK (btrim(provider_reference) <> ''),
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('MATCHED', 'OPEN', 'INVESTIGATING', 'RESOLVED')),
    expected_amount_minor BIGINT NOT NULL
        CHECK (expected_amount_minor > 0),
    actual_amount_minor BIGINT NOT NULL
        CHECK (actual_amount_minor >= 0),
    expected_currency VARCHAR(3) NOT NULL
        CHECK (expected_currency ~ '^[A-Z]{3}$'),
    actual_currency VARCHAR(3) NOT NULL
        CHECK (actual_currency ~ '^[A-Z]{3}$'),
    discrepancy_reason VARCHAR(120) NULL,
    resolution_reason VARCHAR(500) NULL,
    actor_external_subject VARCHAR(255) NOT NULL
        CHECK (btrim(actor_external_subject) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ NULL,

    CONSTRAINT uq_reconciliation_cases_provider_reference
        UNIQUE (provider_reference),
    CONSTRAINT ck_reconciliation_cases_resolution
        CHECK (
            (status = 'RESOLVED' AND resolution_reason IS NOT NULL AND resolved_at IS NOT NULL)
            OR
            (status <> 'RESOLVED' AND resolved_at IS NULL)
        )
);

CREATE INDEX idx_reconciliation_cases_status_created_at
    ON reconciliation_cases (status, created_at);

CREATE INDEX idx_reconciliation_cases_batch
    ON reconciliation_cases (settlement_batch_id);
