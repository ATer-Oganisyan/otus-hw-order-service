create table orders (
id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
request_id VARCHAR(255) NOT NULL UNIQUE KEY,
slot_id VARCHAR(255) NULL,
user_id VARCHAR(255) NOT NULL,
created_at TIMESTAMP not null default CURRENT_TIMESTAMP,
payment_status_id TINYINT NOT NULL default 1,
delivery_status_id TINYINT NOT NULL default 1,
order_status_id TINYINT NOT NULL default 1
);

create table order_items (
catalog_id INT NOT NULL,
order_id INT NOT NULL,
cnt INT NOT NULL,
request_id VARCHAR(255) NOT NULL
);