-- Add explicit expiry lifecycle state to fiat_order.

ALTER TABLE fiat_order
    ADD COLUMN IF NOT EXISTS expire_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expired_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS terminal_reason VARCHAR(64);

UPDATE fiat_order
SET expire_at = COALESCE(expire_at, created_at + INTERVAL '7 days')
WHERE expire_at IS NULL;

ALTER TABLE fiat_order
    ALTER COLUMN expire_at SET NOT NULL;

ALTER TABLE fiat_order DROP CONSTRAINT IF EXISTS chk_fiat_order_status;
ALTER TABLE fiat_order
    ADD CONSTRAINT chk_fiat_order_status CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'EXPIRED'));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_fiat_order_expired_requires_terminal_reason'
    ) THEN
        ALTER TABLE fiat_order
            ADD CONSTRAINT chk_fiat_order_expired_requires_terminal_reason
                CHECK (
                    status <> 'EXPIRED'
                    OR (
                        expired_at IS NOT NULL
                        AND terminal_reason IS NOT NULL
                        AND length(trim(terminal_reason)) > 0
                    )
                );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fiat_order_pending_expiry
    ON fiat_order(status, expire_at, created_at);
