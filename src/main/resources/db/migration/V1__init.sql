CREATE TABLE charges (
    id             UUID PRIMARY KEY,
    txid           VARCHAR(35)  NOT NULL UNIQUE,
    amount_cents   BIGINT       NOT NULL CHECK (amount_cents > 0),
    pix_key        VARCHAR(77)  NOT NULL,
    description    VARCHAR(140),
    status         VARCHAR(16)  NOT NULL,
    copy_paste     VARCHAR(512),
    end_to_end_id  VARCHAR(32)  UNIQUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    paid_at        TIMESTAMPTZ,
    version        BIGINT       NOT NULL
);

CREATE INDEX ix_charges_status_expires ON charges (status, expires_at);

CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    aggregate_id  VARCHAR(64)  NOT NULL,
    type          VARCHAR(64)  NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ,
    attempts      INTEGER      NOT NULL DEFAULT 0
);

-- Partial index: the publisher only ever scans unpublished rows.
CREATE INDEX ix_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE idempotency_keys (
    idempotency_key  VARCHAR(64)  PRIMARY KEY,
    request_hash     VARCHAR(64)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    response_body    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL
);
