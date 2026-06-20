-- The customer's preferred channel for transactional notifications (order +
-- payment events). Resolved server-side: SMS only takes effect when a phone is
-- on file, otherwise delivery falls back to EMAIL. Defaults to EMAIL so existing
-- users keep receiving email exactly as before.

ALTER TABLE users
    ADD COLUMN notification_channel VARCHAR(16) NOT NULL DEFAULT 'EMAIL';
