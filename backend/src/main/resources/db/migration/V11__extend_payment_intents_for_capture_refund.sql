ALTER TABLE payment_intents
    ADD COLUMN capture_journal_entry_id UUID NULL
        REFERENCES journal_entries(id)
        ON DELETE RESTRICT,
    ADD COLUMN refund_journal_entry_id UUID NULL
        REFERENCES journal_entries(id)
        ON DELETE RESTRICT,
    ADD COLUMN capture_idempotency_key VARCHAR(255) NULL
        CHECK (capture_idempotency_key IS NULL OR btrim(capture_idempotency_key) <> ''),
    ADD COLUMN refund_idempotency_key VARCHAR(255) NULL
        CHECK (refund_idempotency_key IS NULL OR btrim(refund_idempotency_key) <> ''),
    ADD COLUMN refunded_at TIMESTAMPTZ NULL;

ALTER TABLE payment_intents
    DROP CONSTRAINT ck_payment_intents_status_timestamps;

ALTER TABLE payment_intents
    DROP CONSTRAINT payment_intents_status_check;

ALTER TABLE payment_intents
    ADD CONSTRAINT payment_intents_status_check
        CHECK (status IN (
            'CREATED',
            'AUTHORIZED',
            'CAPTURED',
            'CANCELED',
            'EXPIRED',
            'FAILED',
            'REFUNDED'
        ));

ALTER TABLE payment_intents
    ADD CONSTRAINT ck_payment_intents_status_timestamps
        CHECK (
            (status = 'CREATED'
                AND funds_hold_id IS NULL
                AND capture_journal_entry_id IS NULL
                AND refund_journal_entry_id IS NULL
                AND authorized_at IS NULL
                AND canceled_at IS NULL
                AND captured_at IS NULL
                AND refunded_at IS NULL
                AND expired_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'AUTHORIZED'
                AND funds_hold_id IS NOT NULL
                AND capture_journal_entry_id IS NULL
                AND refund_journal_entry_id IS NULL
                AND authorized_at IS NOT NULL
                AND canceled_at IS NULL
                AND captured_at IS NULL
                AND refunded_at IS NULL
                AND expired_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'CAPTURED'
                AND funds_hold_id IS NOT NULL
                AND capture_journal_entry_id IS NOT NULL
                AND refund_journal_entry_id IS NULL
                AND authorized_at IS NOT NULL
                AND captured_at IS NOT NULL
                AND refunded_at IS NULL
                AND canceled_at IS NULL
                AND expired_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'REFUNDED'
                AND funds_hold_id IS NOT NULL
                AND capture_journal_entry_id IS NOT NULL
                AND refund_journal_entry_id IS NOT NULL
                AND authorized_at IS NOT NULL
                AND captured_at IS NOT NULL
                AND refunded_at IS NOT NULL
                AND canceled_at IS NULL
                AND expired_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'CANCELED'
                AND funds_hold_id IS NOT NULL
                AND capture_journal_entry_id IS NULL
                AND refund_journal_entry_id IS NULL
                AND authorized_at IS NOT NULL
                AND canceled_at IS NOT NULL
                AND captured_at IS NULL
                AND refunded_at IS NULL
                AND expired_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'EXPIRED'
                AND expired_at IS NOT NULL
                AND canceled_at IS NULL
                AND refunded_at IS NULL
                AND failed_at IS NULL)
            OR
            (status = 'FAILED'
                AND failed_at IS NOT NULL
                AND canceled_at IS NULL
                AND refunded_at IS NULL
                AND expired_at IS NULL)
        );

CREATE UNIQUE INDEX ux_payment_intents_capture_idempotency
    ON payment_intents (capture_idempotency_key)
    WHERE capture_idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX ux_payment_intents_refund_idempotency
    ON payment_intents (refund_idempotency_key)
    WHERE refund_idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX ux_payment_intents_capture_journal
    ON payment_intents (capture_journal_entry_id)
    WHERE capture_journal_entry_id IS NOT NULL;

CREATE UNIQUE INDEX ux_payment_intents_refund_journal
    ON payment_intents (refund_journal_entry_id)
    WHERE refund_journal_entry_id IS NOT NULL;
