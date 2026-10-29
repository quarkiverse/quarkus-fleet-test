CREATE TABLE payments (id SERIAL PRIMARY KEY, account TEXT, amount INT, instance INT);
-- the jdbc idempotency store's table, owned by the application, as the extension's documentation gives it
CREATE TABLE idempotency_entry (
    id VARCHAR(255) PRIMARY KEY,
    fingerprint VARCHAR(255),
    in_flight SMALLINT NOT NULL,
    response BYTEA,
    lock_expires_at BIGINT NOT NULL,
    response_expires_at BIGINT
);
