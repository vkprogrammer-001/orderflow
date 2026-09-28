CREATE TABLE payments (
    id                 UUID PRIMARY KEY,
    order_id           UUID           NOT NULL UNIQUE,
    customer_id        VARCHAR(64)    NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    method             VARCHAR(16)    NOT NULL,
    status             VARCHAR(16)    NOT NULL,
    provider_reference VARCHAR(64),
    decline_reason     VARCHAR(255),
    created_at         TIMESTAMPTZ    NOT NULL,
    version            BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX idx_payments_customer ON payments (customer_id, created_at DESC);

CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    topic        VARCHAR(128) NOT NULL,
    message_key  VARCHAR(128) NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(500)
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_messages (
    consumer     VARCHAR(64) NOT NULL,
    event_id     UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
