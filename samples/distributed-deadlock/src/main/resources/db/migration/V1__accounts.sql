CREATE TABLE accounts (id INT PRIMARY KEY, balance INT NOT NULL);
-- two accounts transfers move money between, same opening balance
INSERT INTO accounts (id, balance) VALUES (1, 100), (2, 100);
