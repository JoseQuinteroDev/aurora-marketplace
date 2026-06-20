-- Optional contact phone for the customer, captured at registration and carried
-- on the ORDER_CREATED event so the notification-service can send an order SMS
-- alongside the confirmation email.

ALTER TABLE users
    ADD COLUMN phone VARCHAR(32);
