CREATE TABLE wallets (id INT PRIMARY KEY, balance INT NOT NULL);
-- two wallets with the same opening balance, one per test
INSERT INTO wallets (id, balance) VALUES (1, 100), (2, 100);
