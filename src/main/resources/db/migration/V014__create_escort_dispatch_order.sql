-- 派單護航系統：訂單主表

CREATE TABLE IF NOT EXISTS escort_dispatch_order (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(32) NOT NULL UNIQUE,
    guild_id BIGINT NOT NULL,
    assigned_by_user_id BIGINT NOT NULL,
    escort_user_id BIGINT NOT NULL,
    customer_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_escort_dispatch_order_status
        CHECK (status IN ('PENDING_CONFIRMATION', 'CONFIRMED')),
    CONSTRAINT chk_escort_dispatch_order_users_not_same
        CHECK (escort_user_id <> customer_user_id)
);

CREATE INDEX IF NOT EXISTS idx_escort_dispatch_order_guild_id
    ON escort_dispatch_order(guild_id);

CREATE INDEX IF NOT EXISTS idx_escort_dispatch_order_status
    ON escort_dispatch_order(status);

CREATE INDEX IF NOT EXISTS idx_escort_dispatch_order_escort_user_id
    ON escort_dispatch_order(escort_user_id);

-- 觸發器：自動更新 updated_at
DROP TRIGGER IF EXISTS update_escort_dispatch_order_updated_at ON escort_dispatch_order;
CREATE TRIGGER update_escort_dispatch_order_updated_at
    BEFORE UPDATE ON escort_dispatch_order
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE escort_dispatch_order IS '派單護航訂單';
COMMENT ON COLUMN escort_dispatch_order.order_number IS '唯一訂單編號';
COMMENT ON COLUMN escort_dispatch_order.assigned_by_user_id IS '建立派單的管理員 user_id';
COMMENT ON COLUMN escort_dispatch_order.escort_user_id IS '被指派護航者 user_id';
COMMENT ON COLUMN escort_dispatch_order.customer_user_id IS '派單客戶 user_id';
COMMENT ON COLUMN escort_dispatch_order.status IS '訂單狀態：PENDING_CONFIRMATION 或 CONFIRMED';
