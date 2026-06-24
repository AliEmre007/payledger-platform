CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_type VARCHAR(100) NOT NULL
        CHECK (action_type = upper(action_type)),
    actor_external_subject VARCHAR(255) NULL,
    actor_customer_id UUID NULL
        REFERENCES customers(id)
        ON DELETE RESTRICT,
    resource_type VARCHAR(100) NOT NULL
        CHECK (resource_type = upper(resource_type)),
    resource_id UUID NOT NULL,
    trace_id UUID NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(metadata) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_resource
    ON audit_events (resource_type, resource_id);

CREATE INDEX idx_audit_events_actor_customer
    ON audit_events (actor_customer_id);

CREATE INDEX idx_audit_events_created_at
    ON audit_events (created_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL
        CHECK (event_type = upper(event_type)),
    aggregate_type VARCHAR(100) NOT NULL
        CHECK (aggregate_type = upper(aggregate_type)),
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL
        CHECK (jsonb_typeof(payload) = 'object'),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING')),
    attempt_count INTEGER NOT NULL DEFAULT 0
        CHECK (attempt_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_events_status_created_at
    ON outbox_events (status, created_at);

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id);

CREATE OR REPLACE FUNCTION reject_audit_events_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_reject_update
BEFORE UPDATE ON audit_events
FOR EACH ROW
EXECUTE FUNCTION reject_audit_events_mutation();

CREATE TRIGGER trg_audit_events_reject_delete
BEFORE DELETE ON audit_events
FOR EACH ROW
EXECUTE FUNCTION reject_audit_events_mutation();

CREATE OR REPLACE FUNCTION reject_outbox_events_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'outbox_events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_outbox_events_reject_update
BEFORE UPDATE ON outbox_events
FOR EACH ROW
EXECUTE FUNCTION reject_outbox_events_mutation();

CREATE TRIGGER trg_outbox_events_reject_delete
BEFORE DELETE ON outbox_events
FOR EACH ROW
EXECUTE FUNCTION reject_outbox_events_mutation();
