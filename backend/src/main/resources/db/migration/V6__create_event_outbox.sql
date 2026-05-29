-- Transactional Outbox.
--
-- Domain events are written to this table inside the SAME database transaction
-- as the business change (order created, payment confirmed/failed). A separate
-- relay then publishes PENDING rows to Kafka and marks them PUBLISHED. This
-- removes the dual-write problem: an event exists if and only if its business
-- transaction committed, and Kafka downtime only delays delivery (the rows
-- stay PENDING) instead of losing the event.
CREATE TABLE event_outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(60)  NOT NULL,
    aggregate_id   VARCHAR(120) NOT NULL,
    event_type     VARCHAR(80)  NOT NULL,
    topic          VARCHAR(150) NOT NULL,
    message_key    VARCHAR(200),
    payload        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ
);

-- The relay polls the oldest unpublished rows; this index keeps that cheap.
CREATE INDEX idx_event_outbox_status_created ON event_outbox (status, created_at);
