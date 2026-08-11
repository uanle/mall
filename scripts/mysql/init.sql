CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    price_cent BIGINT NOT NULL,
    status TINYINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mall_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    role VARCHAR(32) NOT NULL,
    level VARCHAR(32) NOT NULL,
    status TINYINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_role_level (role, level),
    KEY idx_user_status_created (status, created_at)
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
    payer_user_id BIGINT NULL,
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

CREATE TABLE IF NOT EXISTS shopping_cart_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    selected TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    KEY idx_cart_user_selected (user_id, selected),
    KEY idx_cart_product (product_id)
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
    payer_user_id BIGINT NULL,
    paid_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
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

CREATE TABLE IF NOT EXISTS user_api_access_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NULL,
    request_id VARCHAR(128) NULL,
    user_id BIGINT NULL,
    user_role VARCHAR(32) NULL,
    route_id VARCHAR(128) NULL,
    http_method VARCHAR(16) NOT NULL,
    path VARCHAR(512) NOT NULL,
    status INT NOT NULL,
    success TINYINT NOT NULL,
    duration_ms BIGINT NOT NULL,
    client_ip VARCHAR(64) NULL,
    error_type VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_api_access_user_created (user_id, created_at),
    KEY idx_api_access_path_created (path, created_at),
    KEY idx_api_access_status_created (status, created_at),
    KEY idx_api_access_trace (trace_id),
    KEY idx_api_access_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO product (id, name, price_cent, status)
VALUES (2001, 'Seckill Phone', 199900, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), price_cent = VALUES(price_cent), status = VALUES(status);

INSERT INTO mall_user (id, username, password_hash, role, level, status)
VALUES
    (1, 'admin', '7a3387bec43778ba5913f99d361cad9cc7779538991050f5d163643240422bb2', 'ADMIN', 'NONE', 1),
    (2, 'user', 'b95a49530e6a9477a2f5d9b7d4908fe1508086e952f57dbff206dd538a687060', 'USER', 'NORMAL', 1),
    (3, 'vip', 'b95a49530e6a9477a2f5d9b7d4908fe1508086e952f57dbff206dd538a687060', 'USER', 'VIP', 1),
    (4, 'svip', 'b95a49530e6a9477a2f5d9b7d4908fe1508086e952f57dbff206dd538a687060', 'USER', 'SVIP', 1)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    role = VALUES(role),
    level = VALUES(level),
    status = VALUES(status);

INSERT INTO product_inventory (product_id, available_stock, locked_stock, sold_stock)
VALUES (2001, 1000, 0, 0)
ON DUPLICATE KEY UPDATE available_stock = VALUES(available_stock);

INSERT INTO seckill_activity (id, product_id, start_time, end_time, total_stock, status)
VALUES (1001, 2001, '2026-01-01 00:00:00.000', '2028-01-01 00:00:00.000', 1000, 1)
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id), total_stock = VALUES(total_stock), status = VALUES(status);

-- ============================================================
-- 商品基础数据
-- status:
-- 1 = 上架
-- 0 = 下架
-- ============================================================

INSERT INTO product (id, name, price_cent, status)
VALUES
    -- 手机 / 数码
    (2001, 'Seckill Phone',             199900, 1),
    (2002, 'Wireless Headphones',        29900, 1),
    (2003, 'Mechanical Keyboard',        49900, 1),
    (2004, 'Gaming Mouse',               19900, 1),
    (2005, 'Smart Watch',                89900, 1),
    (2006, 'Tablet 10.9 Inch',          159900, 1),
    (2007, 'Flagship Smartphone 256GB', 499900, 1),
    (2008, 'Budget Smartphone 128GB',   129900, 1),
    (2009, 'Bluetooth Speaker',          23900, 1),
    (2010, 'Noise Cancelling Headset',   69900, 1),

    -- 电脑 / 办公
    (2011, 'Ultrabook Laptop 14 Inch',  599900, 1),
    (2012, 'Gaming Laptop RTX Series',  899900, 1),
    (2013, '27 Inch 4K Monitor',        189900, 1),
    (2014, 'USB-C Docking Station',      39900, 1),
    (2015, 'Portable SSD 1TB',           59900, 1),
    (2016, 'Wireless Office Mouse',       9900, 1),
    (2017, 'Office Keyboard',            12900, 1),

    -- 智能家居
    (2018, 'Smart Desk Lamp',            15900, 1),
    (2019, 'Smart Home Camera',          25900, 1),
    (2020, 'Robot Vacuum Cleaner',      169900, 1),

    -- 生活用品
    (2021, 'Electric Toothbrush',        19900, 1),
    (2022, 'Portable Power Bank 20000mAh',15900, 1),
    (2023, 'Fast Charger 65W',            9900, 1),
    (2024, 'USB-C Cable 2m',              2900, 1),
    (2025, 'Laptop Stand',               12900, 1),

    -- 测试特殊状态商品
    (2026, 'Limited Edition Keyboard',   89900, 1),
    (2027, 'Clearance Headphones',       12900, 1),
    (2028, 'Discontinued Smart Watch',   49900, 0),
    (2029, 'New Release Tablet',        229900, 1),
    (2030, 'Premium 4K Monitor',        329900, 0)

ON DUPLICATE KEY UPDATE
                     name = VALUES(name),
                     price_cent = VALUES(price_cent),
                     status = VALUES(status);


-- ============================================================
-- 商品库存
--
-- 每一个 product 都对应一条库存记录
--
-- available_stock = 可售库存
-- locked_stock    = 已锁定但尚未完成订单的库存
-- sold_stock      = 已售库存
-- ============================================================

INSERT INTO product_inventory
(
    product_id,
    available_stock,
    locked_stock,
    sold_stock
)
VALUES
    -- ========================================================
    -- 秒杀 / 热门商品：库存较大
    -- ========================================================
    (2001, 950,  20, 30),
    (2002, 780,  10, 210),
    (2003, 560,  15, 125),
    (2004, 1200, 30, 270),
    (2005, 420,  10, 70),
    (2006, 310,   5, 85),

    -- ========================================================
    -- 手机数码
    -- ========================================================
    (2007, 180,  12, 58),
    (2008, 650,  20, 130),
    (2009, 840,  15, 145),
    (2010, 390,   8, 102),

    -- ========================================================
    -- 电脑办公
    -- ========================================================
    (2011, 120,   5, 35),
    (2012,  80,   3, 17),
    (2013, 240,   8, 52),
    (2014, 520,  12, 68),
    (2015, 460,  10, 130),
    (2016, 1500, 20, 480),
    (2017, 1100, 15, 285),

    -- ========================================================
    -- 智能家居
    -- ========================================================
    (2018, 630,  10, 160),
    (2019, 370,   5, 125),
    (2020, 140,   6, 54),

    -- ========================================================
    -- 生活用品
    -- ========================================================
    (2021, 720,  12, 168),
    (2022, 950,  15, 235),
    (2023, 1800, 30, 470),
    (2024, 3000, 50, 950),
    (2025, 680,  10, 210),

    -- ========================================================
    -- 特殊库存商品
    -- ========================================================

    -- 库存很少：适合测试秒杀竞争
    (2026, 10, 0, 90),

    -- 清仓商品
    (2027, 35, 2, 463),

    -- 已下架，但仍保留历史库存信息
    (2028, 0, 0, 300),

    -- 新商品，库存充足，暂时销量低
    (2029, 500, 0, 0),

    -- 已下架
    (2030, 0, 0, 120)

ON DUPLICATE KEY UPDATE
                     available_stock = VALUES(available_stock),
                     locked_stock = VALUES(locked_stock),
                     sold_stock = VALUES(sold_stock);


-- ============================================================
-- 秒杀活动
--
-- status:
-- 1 = 启用
-- 0 = 禁用
--
-- 注意：
-- activity.product_id 都能在 product 中找到
-- ============================================================

INSERT INTO seckill_activity
(
    id,
    product_id,
    start_time,
    end_time,
    total_stock,
    status
)
VALUES
    -- 长期测试活动
    (1001, 2001,
     '2026-01-01 00:00:00.000',
     '2028-01-01 00:00:00.000',
     1000, 1),

    -- 当前进行中的普通秒杀
    (1002, 2002,
     '2026-08-01 00:00:00.000',
     '2026-08-31 23:59:59.999',
     500, 1),

    (1003, 2003,
     '2026-08-05 00:00:00.000',
     '2026-08-20 23:59:59.999',
     300, 1),

    (1004, 2004,
     '2026-08-08 00:00:00.000',
     '2026-08-15 23:59:59.999',
     800, 1),

    -- 高价值商品秒杀
    (1005, 2007,
     '2026-08-10 00:00:00.000',
     '2026-08-12 23:59:59.999',
     100, 1),

    -- 低价高并发商品
    (1006, 2023,
     '2026-08-10 00:00:00.000',
     '2026-08-20 23:59:59.999',
     1000, 1),

    (1007, 2024,
     '2026-08-10 00:00:00.000',
     '2026-08-20 23:59:59.999',
     2000, 1),

    -- 极低库存，用于测试超卖
    (1008, 2026,
     '2026-08-10 00:00:00.000',
     '2026-08-15 23:59:59.999',
     10, 1),

    -- 已结束活动
    (1009, 2027,
     '2026-07-01 00:00:00.000',
     '2026-07-31 23:59:59.999',
     200, 0),

    -- 尚未开始
    (1010, 2029,
     '2026-09-01 00:00:00.000',
     '2026-09-10 23:59:59.999',
     300, 1)

ON DUPLICATE KEY UPDATE
                     product_id = VALUES(product_id),
                     start_time = VALUES(start_time),
                     end_time = VALUES(end_time),
                     total_stock = VALUES(total_stock),
                     status = VALUES(status);
