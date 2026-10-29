CREATE TABLE outbox (id SERIAL PRIMARY KEY, status TEXT NOT NULL);
-- one row per notification actually sent, by whichever instance sent it
CREATE TABLE sent_log (notification_id INT, instance INT);
