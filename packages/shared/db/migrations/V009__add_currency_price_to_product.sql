-- Flyway Migration V009
-- This migration adds currency_price column to the product table for direct currency purchase functionality.
-- Version: V009

-- Add currency_price column to product table
-- NULL means the product cannot be purchased with currency (redemption code only)
-- Positive value means the product can be purchased with the specified currency amount
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product' AND column_name = 'currency_price'
    ) THEN
        ALTER TABLE product ADD COLUMN currency_price BIGINT;
    END IF;
END $$;

-- Add check constraint to ensure currency_price is non-negative when specified
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'product_currency_price_non_negative'
    ) THEN
        ALTER TABLE product
        ADD CONSTRAINT product_currency_price_non_negative CHECK (
            currency_price IS NULL OR currency_price >= 0
        );
    END IF;
END $$;

-- Create index for finding products with currency prices (for shop purchase menu)
CREATE INDEX IF NOT EXISTS idx_product_currency_price
    ON product (guild_id, currency_price) WHERE currency_price IS NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN product.currency_price IS 'Optional: Currency price for direct purchase. NULL means currency purchase not available. Positive value means purchase with specified currency amount.';
