ALTER TABLE trade_order
    ADD COLUMN payer_user_id BIGINT NULL AFTER request_id,
    ADD COLUMN paid_at DATETIME(3) NULL AFTER payer_user_id,
    ADD COLUMN completed_at DATETIME(3) NULL AFTER paid_at;

ALTER TABLE retail_order
    ADD COLUMN payer_user_id BIGINT NULL AFTER idempotency_key;
