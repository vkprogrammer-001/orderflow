CREATE TABLE orders (
    id             UUID PRIMARY KEY,
    customer_id    VARCHAR(64)  NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    status_reason  VARCHAR(255),
    payment_method VARCHAR(16)  NOT NULL,
    total_amount   NUMERIC(14, 2),
    currency       VARCHAR(3),
    payment_id     UUID,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0
);
-- Serves the watchdog (status + age) and the status-filtered list endpoint.
CREATE INDEX idx_orders_status_updated ON orders (status, updated_at);
CREATE INDEX idx_orders_created ON orders (created_at DESC);
CREATE INDEX idx_orders_customer ON orders (customer_id, created_at DESC);

CREATE TABLE order_lines (
    id         BIGSERIAL PRIMARY KEY,
    order_id   UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sku        VARCHAR(64) NOT NULL,
    quantity   INT         NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2)
);
CREATE INDEX idx_order_lines_order ON order_lines (order_id);

CREATE TABLE idempotency_keys (
    idem_key     VARCHAR(100) PRIMARY KEY,
    request_hash VARCHAR(64)  NOT NULL,
    order_id     UUID         NOT NULL REFERENCES orders (id),
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);

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
