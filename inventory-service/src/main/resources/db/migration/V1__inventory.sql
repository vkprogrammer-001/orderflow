CREATE TABLE products (
    sku        VARCHAR(64) PRIMARY KEY,
    name       VARCHAR(120)   NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price > 0),
    currency   VARCHAR(3)     NOT NULL,
    available  INT            NOT NULL CHECK (available >= 0),
    reserved   INT            NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    version    BIGINT         NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE reservations (
    order_id   UUID PRIMARY KEY,
    status     VARCHAR(16)  NOT NULL,
    reason     VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE reservation_lines (
    order_id   UUID           NOT NULL REFERENCES reservations (order_id) ON DELETE CASCADE,
    sku        VARCHAR(64)    NOT NULL REFERENCES products (sku),
    quantity   INT            NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
);
CREATE INDEX idx_reservation_lines_order ON reservation_lines (order_id);

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
