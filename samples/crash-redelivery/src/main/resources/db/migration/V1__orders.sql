CREATE TABLE received (order_id TEXT, instance INT);
CREATE TABLE shipped_orders (order_id TEXT PRIMARY KEY);
CREATE TABLE shipments (order_id TEXT, instance INT);
