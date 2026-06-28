-- Optimistic locking (OWASP A04 — insecure design / concurrency integrity).
-- Adds a JPA @Version column to the entities whose state can be mutated by two
-- requests at once: inventory (the admin/batch stock-movement path is an unlocked
-- read-modify-write; checkout already takes a pessimistic row lock), orders (status
-- transitions: payment confirmation vs. admin status change vs. refund) and payments
-- (two concurrent payment submissions on one order). A concurrent lost update now
-- fails with ObjectOptimisticLockingFailureException -> HTTP 409 instead of silently
-- overwriting another writer.
--
-- NOT NULL DEFAULT 0 backfills any existing rows; Hibernate manages the value going forward.

ALTER TABLE inventory ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payments  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
