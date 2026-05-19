ALTER TABLE fiat_order
    ADD COLUMN IF NOT EXISTS buyer_notified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reward_granted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reconciliation_processing_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reconciliation_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reconciliation_next_attempt_at TIMESTAMPTZ;

ALTER TABLE product DROP CONSTRAINT IF EXISTS product_backend_api_url_http;
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_backend_api_url_https;
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_backend_api_url_not_private_addr;
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_auto_escort_requires_backend_api;
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_auto_escort_requires_option;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_auto_escort_requires_option'
    ) THEN
        ALTER TABLE product
            ADD CONSTRAINT product_auto_escort_requires_option
                CHECK (
                    NOT auto_create_escort_order
                    OR (
                        escort_option_code IS NOT NULL
                        AND length(trim(escort_option_code)) > 0
                    )
                );
    END IF;
END $$;

COMMENT ON COLUMN product.escort_option_code IS
    'Escort order option code used when auto_create_escort_order is enabled.';

ALTER TABLE product DROP COLUMN IF EXISTS backend_api_url;
