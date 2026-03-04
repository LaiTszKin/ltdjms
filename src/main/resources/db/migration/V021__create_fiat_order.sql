-- Fiat order lifecycle for ECPay payment callback handling.

CREATE TABLE IF NOT EXISTS fiat_order (
    id BIGSERIAL PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    order_number VARCHAR(32) NOT NULL UNIQUE,
    payment_no VARCHAR(32) NOT NULL,
    amount_twd BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    trade_status VARCHAR(32),
    payment_message VARCHAR(255),
    paid_at TIMESTAMPTZ,
    fulfilled_at TIMESTAMPTZ,
    admin_notified_at TIMESTAMPTZ,
    last_callback_payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_fiat_order_amount_positive CHECK (amount_twd > 0),
    CONSTRAINT chk_fiat_order_status CHECK (status IN ('PENDING_PAYMENT', 'PAID'))
);

CREATE INDEX IF NOT EXISTS idx_fiat_order_guild_status
    ON fiat_order(guild_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_fiat_order_buyer
    ON fiat_order(guild_id, buyer_user_id, created_at DESC);

DROP TRIGGER IF EXISTS update_fiat_order_updated_at ON fiat_order;
CREATE TRIGGER update_fiat_order_updated_at
    BEFORE UPDATE ON fiat_order
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE fiat_order IS
    'Fiat product orders waiting for ECPay payment callback confirmation.';
COMMENT ON COLUMN fiat_order.order_number IS
    'ECPay MerchantTradeNo.';
COMMENT ON COLUMN fiat_order.payment_no IS
    'CVS payment code for buyer.';
