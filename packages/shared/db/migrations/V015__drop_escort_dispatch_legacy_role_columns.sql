-- 派單護航系統：移除舊版遺留角色欄位
-- 背景：先前版本曾將 escort_role_id/customer_role_id 設為 NOT NULL，
-- 在移除派單角色選項後會導致建立訂單失敗（欄位值為 null）。

ALTER TABLE IF EXISTS escort_dispatch_order
    DROP COLUMN IF EXISTS escort_role_id;

ALTER TABLE IF EXISTS escort_dispatch_order
    DROP COLUMN IF EXISTS customer_role_id;
