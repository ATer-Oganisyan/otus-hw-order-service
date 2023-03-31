create table orders (
id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
client_name VARCHAR(255) NOT NULL,
client_contact VARCHAR(255) NOT NULL,
request_id VARCHAR(255) NOT NULL UNIQUE KEY,
created_at TIMESTAMP not null default CURRENT_TIMESTAMP,
status_id TINYINT NOT NULL
);


create table catalog (
id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
good_code VARCHAR(255) NOT NULL,
good_name VARCHAR(255) NOT NULL,
good_description VARCHAR(255) NOT NULL,
measurement_units VARCHAR(255) NOT NULL,
price_per_unit INT NOT NULL
);

create table order_items (
good_id INT NOT NULL,
order_id INT NOT NULL,
cnt INT NOT NULL
);