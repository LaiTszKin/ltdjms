-- 派單護航系統：新增自動交接來源快照

ALTER TABLE IF EXISTS escort_dispatch_order
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(128),
    ADD COLUMN IF NOT EXISTS source_product_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_product_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source_currency_price BIGINT,
    ADD COLUMN IF NOT EXISTS source_fiat_price_twd BIGINT,
    ADD COLUMN IF NOT EXISTS source_escort_option_code VARCHAR(120);

ALTER TABLE IF EXISTS escort_dispatch_order
    DROP CONSTRAINT IF EXISTS chk_escort_dispatch_order_source_type;

ALTER TABLE IF EXISTS escort_dispatch_order
    ADD CONSTRAINT chk_escort_dispatch_order_source_type
        CHECK (source_type IN ('MANUAL', 'CURRENCY_PURCHASE', 'FIAT_PAYMENT'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_escort_dispatch_order_source_identity
    ON escort_dispatch_order(source_type, source_reference)
    WHERE source_reference IS NOT NULL;

COMMENT ON COLUMN escort_dispatch_order.source_type IS
    '護航訂單來源類型：MANUAL、CURRENCY_PURCHASE、FIAT_PAYMENT';
COMMENT ON COLUMN escort_dispatch_order.source_reference IS
    '自動交接的來源參考（貨幣購買 interaction id 或法幣訂單編號）';
COMMENT ON COLUMN escort_dispatch_order.source_product_id IS
    '來源商品編號快照';
COMMENT ON COLUMN escort_dispatch_order.source_product_name IS
    '來源商品名稱快照';
COMMENT ON COLUMN escort_dispatch_order.source_currency_price IS
    '來源商品貨幣價格快照';
COMMENT ON COLUMN escort_dispatch_order.source_fiat_price_twd IS
    '來源商品法幣價格快照';
COMMENT ON COLUMN escort_dispatch_order.source_escort_option_code IS
    '來源商品護航選項代碼快照';
