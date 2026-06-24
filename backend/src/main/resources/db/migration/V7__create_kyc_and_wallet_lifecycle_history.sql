CREATE TABLE kyc_review_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL
        REFERENCES customers(id)
        ON DELETE RESTRICT,
    from_status VARCHAR(20) NOT NULL
        CHECK (from_status IN ('NOT_STARTED', 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    to_status VARCHAR(20) NOT NULL
        CHECK (to_status IN ('NOT_STARTED', 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    reason VARCHAR(500) NOT NULL
        CHECK (length(trim(reason)) > 0),
    actor_external_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_kyc_review_events_customer_created_at
    ON kyc_review_events (customer_id, created_at);

CREATE TABLE wallet_lifecycle_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL
        REFERENCES wallets(id)
        ON DELETE RESTRICT,
    from_status VARCHAR(20) NOT NULL
        CHECK (from_status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    to_status VARCHAR(20) NOT NULL
        CHECK (to_status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    reason VARCHAR(500) NOT NULL
        CHECK (length(trim(reason)) > 0),
    actor_external_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wallet_lifecycle_events_wallet_created_at
    ON wallet_lifecycle_events (wallet_id, created_at);
