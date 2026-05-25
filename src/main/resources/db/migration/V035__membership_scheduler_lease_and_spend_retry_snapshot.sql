-- Scheduler lease avoids holding pooled connections for advisory locks during long ticks
CREATE TABLE membership_scheduler_lease (
    lock_name    VARCHAR(64) PRIMARY KEY,
    locked_until TIMESTAMPTZ NOT NULL DEFAULT '1970-01-01T00:00:00Z',
    locked_by    VARCHAR(128) NOT NULL DEFAULT ''
);

INSERT INTO membership_scheduler_lease (lock_name, locked_until, locked_by)
VALUES ('settlement', '1970-01-01T00:00:00Z', '')
ON CONFLICT (lock_name) DO NOTHING;

COMMENT ON TABLE membership_scheduler_lease IS
    'Short-lived lease rows for single-instance membership scheduler leadership';

-- Spend retry snapshot columns so membership retry does not re-fetch shop orders
ALTER TABLE membership_spend_retry
  ADD COLUMN IF NOT EXISTS buyer_user_id BIGINT,
  ADD COLUMN IF NOT EXISTS guild_id BIGINT,
  ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS order_list_price_twd BIGINT,
  ADD COLUMN IF NOT EXISTS escort_option_code VARCHAR(64),
  ADD COLUMN IF NOT EXISTS product_fiat_price_twd BIGINT,
  ADD COLUMN IF NOT EXISTS escort_linked BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN membership_spend_retry.buyer_user_id IS
    'Snapshot buyer for membership spend retry without shop order lookup';
