ALTER TABLE outbox_events
    ADD COLUMN processed_at TIMESTAMPTZ NULL,
    ADD COLUMN last_error VARCHAR(500) NULL;

ALTER TABLE outbox_events
    DROP CONSTRAINT outbox_events_status_check;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_status_check
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'));

DROP TRIGGER trg_outbox_events_reject_update ON outbox_events;

CREATE OR REPLACE FUNCTION reject_outbox_events_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'outbox_events are append-only';
    END IF;

    IF TG_OP = 'UPDATE'
        AND NEW.id = OLD.id
        AND NEW.event_id = OLD.event_id
        AND NEW.event_type = OLD.event_type
        AND NEW.aggregate_type = OLD.aggregate_type
        AND NEW.aggregate_id = OLD.aggregate_id
        AND NEW.payload = OLD.payload
        AND NEW.created_at = OLD.created_at
    THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'outbox_events immutable fields cannot be changed';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_outbox_events_reject_update
BEFORE UPDATE ON outbox_events
FOR EACH ROW
EXECUTE FUNCTION reject_outbox_events_mutation();

CREATE TABLE notification_records (
    id UUID PRIMARY KEY,
    outbox_event_id UUID NOT NULL
        REFERENCES outbox_events(id)
        ON DELETE RESTRICT,
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL
        CHECK (event_type = upper(event_type)),
    aggregate_type VARCHAR(100) NOT NULL
        CHECK (aggregate_type = upper(aggregate_type)),
    aggregate_id UUID NOT NULL,
    recipient_type VARCHAR(30) NOT NULL
        CHECK (recipient_type IN ('CUSTOMER', 'OPERATIONS')),
    recipient_reference VARCHAR(255) NOT NULL
        CHECK (btrim(recipient_reference) <> ''),
    channel VARCHAR(30) NOT NULL
        CHECK (channel IN ('LOCAL_LOG')),
    subject VARCHAR(120) NOT NULL
        CHECK (btrim(subject) <> ''),
    body VARCHAR(1000) NOT NULL
        CHECK (btrim(body) <> ''),
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0
        CHECK (attempt_count >= 0),
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ NULL,

    CONSTRAINT uq_notification_records_outbox_event
        UNIQUE (outbox_event_id),
    CONSTRAINT uq_notification_records_event
        UNIQUE (event_id)
);

CREATE INDEX idx_notification_records_status_created_at
    ON notification_records (status, created_at);

CREATE TABLE notification_delivery_attempts (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL
        REFERENCES notification_records(id)
        ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL
        CHECK (attempt_number > 0),
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('SUCCEEDED', 'FAILED')),
    error_message VARCHAR(500) NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_notification_delivery_attempts_number
        UNIQUE (notification_id, attempt_number)
);

CREATE INDEX idx_notification_delivery_attempts_notification
    ON notification_delivery_attempts (notification_id, attempted_at);
