CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    price_cent BIGINT NOT NULL,
    status TINYINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS seckill_activity (
    id BIGINT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) NOT NULL,
    total_stock INT NOT NULL,
    status TINYINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_activity_product_status (product_id, status),
    KEY idx_activity_time_status (start_time, end_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_inventory (
    product_id BIGINT PRIMARY KEY,
    available_stock INT NOT NULL,
    locked_stock INT NOT NULL DEFAULT 0,
    sold_stock INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_inventory_available (available_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS retail_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount_cent BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    paid_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_retail_order_no (order_no),
    UNIQUE KEY uk_retail_idempotency_key (idempotency_key),
    KEY idx_retail_user_created (user_id, created_at),
    KEY idx_retail_status_created (status, created_at),
    KEY idx_retail_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_deduct_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_inventory_order_no (order_no),
    KEY idx_inventory_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trade_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    amount_cent BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_request_id (request_id),
    UNIQUE KEY uk_user_activity (user_id, activity_id),
    KEY idx_order_status_created (status, created_at),
    KEY idx_order_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock_deduct_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_stock_request (request_id),
    KEY idx_stock_user_activity (user_id, activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO product (id, name, price_cent, status)
VALUES (2001, 'Seckill Phone', 199900, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), price_cent = VALUES(price_cent), status = VALUES(status);

INSERT INTO product_inventory (product_id, available_stock, locked_stock, sold_stock)
VALUES (2001, 1000, 0, 0)
ON DUPLICATE KEY UPDATE available_stock = VALUES(available_stock);

INSERT INTO seckill_activity (id, product_id, start_time, end_time, total_stock, status)
VALUES (1001, 2001, '2026-01-01 00:00:00.000', '2028-01-01 00:00:00.000', 1000, 1)
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), total_stock = VALUES(total_stock), status = VALUES(status);
