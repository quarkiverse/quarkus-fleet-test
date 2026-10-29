CREATE TABLE lease (id INT PRIMARY KEY, owner INT, token BIGINT, expires_at TIMESTAMP);
CREATE TABLE done (item INT, instance INT, token BIGINT);
-- the one lease, free and expired, for the first instance to take
INSERT INTO lease (id, owner, token, expires_at) VALUES (1, -1, 0, now());
