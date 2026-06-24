CREATE TABLE customer_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    customer_id UUID NOT NULL
        REFERENCES customers(id)
        ON DELETE RESTRICT,

    identity_provider VARCHAR(30) NOT NULL,

    external_subject VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_customer_identities_provider
        CHECK (identity_provider IN ('KEYCLOAK')),

    CONSTRAINT uq_customer_identity_provider_subject
        UNIQUE (identity_provider, external_subject),

    CONSTRAINT uq_customer_identity_customer_provider
        UNIQUE (customer_id, identity_provider)
);
