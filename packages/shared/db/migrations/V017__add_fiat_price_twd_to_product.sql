-- Flyway Migration V017
-- This migration adds fiat_price_twd column to product for fiat-only payment flow.
-- Version: V017

-- Add fiat_price_twd column to product table
-- NULL means this product has no configured fiat value.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product' AND column_name = 'fiat_price_twd'
    ) THEN
        ALTER TABLE product ADD COLUMN fiat_price_twd BIGINT;
    END IF;
END $$;

-- Add check constraint to ensure fiat_price_twd is non-negative when specified
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'product_fiat_price_twd_non_negative'
    ) THEN
        ALTER TABLE product
        ADD CONSTRAINT product_fiat_price_twd_non_negative CHECK (
            fiat_price_twd IS NULL OR fiat_price_twd >= 0
        );
    END IF;
END $$;

-- Create index for finding fiat products in a guild
CREATE INDEX IF NOT EXISTS idx_product_fiat_price_twd
    ON product (guild_id, fiat_price_twd) WHERE fiat_price_twd IS NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN product.fiat_price_twd IS 'Optional: Product actual value in TWD for fiat payment flow.';
