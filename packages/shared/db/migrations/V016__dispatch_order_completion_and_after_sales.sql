-- 派單護航系統：完單確認與售後流程

-- 1) 擴充訂單狀態欄位與流程時間欄位
ALTER TABLE IF EXISTS escort_dispatch_order
    ADD COLUMN IF NOT EXISTS completion_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS after_sales_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS after_sales_assignee_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS after_sales_assigned_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS after_sales_closed_at TIMESTAMPTZ;

-- 更新狀態檢查約束
ALTER TABLE IF EXISTS escort_dispatch_order
    DROP CONSTRAINT IF EXISTS chk_escort_dispatch_order_status;

ALTER TABLE IF EXISTS escort_dispatch_order
    ADD CONSTRAINT chk_escort_dispatch_order_status
        CHECK (
            status IN (
                'PENDING_CONFIRMATION',
                'CONFIRMED',
                'PENDING_CUSTOMER_CONFIRMATION',
                'COMPLETED',
                'AFTER_SALES_REQUESTED',
                'AFTER_SALES_IN_PROGRESS',
                'AFTER_SALES_CLOSED'
            )
        );

CREATE INDEX IF NOT EXISTS idx_escort_dispatch_order_completion_requested_at
    ON escort_dispatch_order(completion_requested_at)
    WHERE status = 'PENDING_CUSTOMER_CONFIRMATION';

CREATE INDEX IF NOT EXISTS idx_escort_dispatch_order_after_sales_assignee
    ON escort_dispatch_order(after_sales_assignee_user_id)
    WHERE after_sales_assignee_user_id IS NOT NULL;

-- 2) 售後人員設定表（guild 可配置多位）
CREATE TABLE IF NOT EXISTS dispatch_after_sales_staff (
    guild_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (guild_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_dispatch_after_sales_staff_guild_id
    ON dispatch_after_sales_staff(guild_id);

COMMENT ON TABLE dispatch_after_sales_staff IS '派單系統售後人員設定';
COMMENT ON COLUMN dispatch_after_sales_staff.guild_id IS 'Discord guild_id';
COMMENT ON COLUMN dispatch_after_sales_staff.user_id IS '售後人員 user_id';

COMMENT ON COLUMN escort_dispatch_order.completion_requested_at IS '護航者提交完單時間';
COMMENT ON COLUMN escort_dispatch_order.completed_at IS '訂單完成時間（客戶確認或超時）';
COMMENT ON COLUMN escort_dispatch_order.after_sales_requested_at IS '客戶提交售後時間';
COMMENT ON COLUMN escort_dispatch_order.after_sales_assignee_user_id IS '接手售後人員 user_id';
COMMENT ON COLUMN escort_dispatch_order.after_sales_assigned_at IS '售後接手時間';
COMMENT ON COLUMN escort_dispatch_order.after_sales_closed_at IS '售後結案時間';
