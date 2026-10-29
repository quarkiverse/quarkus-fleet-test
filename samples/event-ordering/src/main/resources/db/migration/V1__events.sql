CREATE TABLE events (
    id SERIAL PRIMARY KEY,
    subscription_id INT NOT NULL,
    seq INT NOT NULL,
    type TEXT NOT NULL,
    claimed BOOLEAN NOT NULL DEFAULT FALSE
);
-- one aggregate: its terminal state is whatever the latest event by seq says it should be
CREATE TABLE subscriptions (id INT PRIMARY KEY, state TEXT NOT NULL, last_seq INT NOT NULL DEFAULT 0);
INSERT INTO subscriptions (id, state, last_seq) VALUES (7, 'pending', 0);
