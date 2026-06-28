-- Checkout idempotency (OWASP A04 — insecure design / safe retries).
-- Binds a client-supplied Idempotency-Key to the order it produced, scoped per user.
-- The UNIQUE(user_id, idempotency_key) constraint is the enforcement point: a retried or
-- double-submitted checkout with the same key resolves to a single order (replay), and two
-- truly concurrent submissions can't both insert — the loser rolls back with no duplicate
-- order/charge. order_id is NOT NULL: a row is only written together with its order.

CREATE TABLE checkout_idempotency_keys (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    order_id        UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_checkout_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_checkout_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_checkout_idempotency_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_checkout_idempotency_user ON checkout_idempotency_keys (user_id);
