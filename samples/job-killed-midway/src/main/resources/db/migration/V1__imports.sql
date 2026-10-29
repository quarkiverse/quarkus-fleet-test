-- an import: how many rows it has, how many are done, which instance works on it and when it last said so
CREATE TABLE imports (id SERIAL PRIMARY KEY, total INT NOT NULL, processed INT NOT NULL DEFAULT 0, owner INT, heartbeat TIMESTAMP);
-- the primary key means a row cannot be imported twice, whoever resumes the import
CREATE TABLE import_rows (import_id INT, row_no INT, instance INT, PRIMARY KEY (import_id, row_no));
